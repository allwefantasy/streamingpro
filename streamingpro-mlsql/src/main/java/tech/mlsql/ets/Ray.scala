package tech.mlsql.ets

import java.net.ServerSocket
import java.util
import java.util.concurrent.atomic.AtomicReference

import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.mlsql.session.MLSQLException
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, Row, SparkSession, SparkUtils}
import org.apache.spark.{MLSQLSparkUtils, TaskContext}
import streaming.dsl.ScriptSQLExec
import streaming.dsl.mmlib._
import streaming.dsl.mmlib.algs.Functions
import streaming.dsl.mmlib.algs.param.{BaseParams, WowParams}
import tech.mlsql.arrow.python.PythonWorkerFactory
import tech.mlsql.arrow.python.iapp.{AppContextImpl, JavaContext}
import tech.mlsql.arrow.python.ispark.SparkContextImp
import tech.mlsql.arrow.python.runner._
import tech.mlsql.common.utils.distribute.socket.server.{ReportHostAndPort, SocketServerInExecutor}
import tech.mlsql.common.utils.lang.sc.ScalaMethodMacros
import tech.mlsql.common.utils.network.NetUtils
import tech.mlsql.common.utils.serder.json.JSONTool
import tech.mlsql.ets.ray.{CollectServerInDriver, DataServer}
import tech.mlsql.schema.parser.SparkSimpleSchemaParser
import tech.mlsql.session.SetSession
import tech.mlsql.version.VersionCompatibility

import scala.collection.mutable.ArrayBuffer

/**
 * 24/12/2019 WilliamZhu(allwefantasy@gmail.com)
 */
class Ray(override val uid: String) extends SQLAlg with VersionCompatibility with Functions with WowParams {
  def this() = this(BaseParams.randomUID())

  //
  override def train(df: DataFrame, path: String, params: Map[String, String]): DataFrame = {

    val spark = df.sparkSession
    val command = JSONTool.parseJson[List[String]](params("parameters")).toArray
    val newdf = command match {
      case Array("on", tableName, code) =>
        distribute_execute(spark, code, tableName)

      case Array("on", tableName, code, "named", targetTable) =>
        val resDf = distribute_execute(spark, code, tableName)
        resDf.createOrReplaceTempView(targetTable)
        resDf
    }
    newdf
  }

  private def distribute_execute(session: SparkSession, code: String, sourceTable: String) = {
    import scala.collection.JavaConverters._
    val context = ScriptSQLExec.context()
    val envSession = new SetSession(session, context.owner)
    val envs = Map(
      ScalaMethodMacros.str(PythonConf.PY_EXECUTE_USER) -> context.owner,
      ScalaMethodMacros.str(PythonConf.PYTHON_ENV) -> "export ARROW_PRE_0_15_IPC_FORMAT=1"
    ) ++
      envSession.fetchPythonEnv.get.collect().map { f =>
        if (f.k == ScalaMethodMacros.str(PythonConf.PYTHON_ENV)) {
          (f.k, f.v + " && export ARROW_PRE_0_15_IPC_FORMAT=1")
        } else {
          (f.k, f.v)
        }

      }.toMap

    val runnerConf = getSchemaAndConf(envSession) ++ configureLogConf
    val timezoneID = session.sessionState.conf.sessionLocalTimeZone
    val df = session.table(sourceTable)


    val refs = new AtomicReference[ArrayBuffer[ReportHostAndPort]]()
    refs.set(ArrayBuffer[ReportHostAndPort]())
    val stopFlag = new AtomicReference[String]()
    val tempSocketServerInDriver = new CollectServerInDriver(refs, stopFlag)


    val targetLen = df.rdd.partitions.length

    val thread = new Thread("temp-data-server-in-spark") {
      override def run(): Unit = {

        val dataSchema = df.schema
        val tempSocketServerHost = tempSocketServerInDriver._host
        val tempSocketServerPort = tempSocketServerInDriver._port
        val timezoneID = session.sessionState.conf.sessionLocalTimeZone

        df.rdd.mapPartitions { iter =>

          val host: String = if (MLSQLSparkUtils.rpcEnv().address == null) NetUtils.getHost
          else MLSQLSparkUtils.rpcEnv().address.host

          val socketRunner = new SparkSocketRunner("wow", host, timezoneID)
          val commonTaskContext = new SparkContextImp(TaskContext.get(), null)
          val enconder = RowEncoder.apply(dataSchema).resolveAndBind()
          val newIter = iter.map { irow =>
            enconder.toRow(irow)
          }
          val Array(_server, _host, _port) = socketRunner.serveToStreamWithArrow(newIter, dataSchema, 1000, commonTaskContext)

          // send server info back
          SocketServerInExecutor.reportHostAndPort(tempSocketServerHost,
            tempSocketServerPort,
            ReportHostAndPort(_host.toString, _port.toString.toInt))

          while (_server != null && !_server.asInstanceOf[ServerSocket].isClosed) {
            Thread.sleep(1 * 1000)
          }
          List[String]().iterator
        }.count()
        logInfo("Exit all data server")
      }
    }
    thread.setDaemon(true)
    thread.start()

    var clockTimes = 20
    if (targetLen != refs.get().length && clockTimes >= 0) {
      Thread.sleep(500)
      clockTimes -= 1
    }
    stopFlag.set("stop")
    if (clockTimes < 0) {
      throw new RuntimeException(s"fail to start data socket server. targetLen:${targetLen} actualLen:${refs.get().length}")
    }
    tempSocketServerInDriver.close
    val targetSchema = SparkSimpleSchemaParser.parse(runnerConf("schema")).asInstanceOf[StructType]

    try {
      import session.implicits._
      val runIn = runnerConf.getOrElse("runIn", "executor")
      val pythonVersion = runnerConf.getOrElse("pythonVersion", "3.6")
      val newdf = session.createDataset[DataServer](refs.get().map(f => DataServer(f.host, f.port, timezoneID))).repartition(1)
      val sourceSchema = newdf.schema
      runIn match {
        case "executor" =>
          val data = newdf.toDF().rdd.mapPartitions { iter =>
            val encoder = RowEncoder.apply(sourceSchema).resolveAndBind()
            val envs4j = new util.HashMap[String, String]()
            envs.foreach(f => envs4j.put(f._1, f._2))

            val batch = new ArrowPythonRunner(
              Seq(ChainedPythonFunctions(Seq(PythonFunction(
                code, envs4j, "python", pythonVersion)))), sourceSchema,
              timezoneID, runnerConf
            )

            val newIter = iter.map { irow =>
              encoder.toRow(irow)
            }
            val commonTaskContext = new SparkContextImp(TaskContext.get(), batch)
            val columnarBatchIter = batch.compute(Iterator(newIter), TaskContext.getPartitionId(), commonTaskContext)
            columnarBatchIter.flatMap { batch =>
              batch.rowIterator.asScala
            }
          }

          SparkUtils.internalCreateDataFrame(session, data, targetSchema, false)
        case "driver" =>
          val dataServers = refs.get().map(f => DataServer(f.host, f.port, timezoneID))
          val encoder = RowEncoder.apply(sourceSchema).resolveAndBind()
          val envs4j = new util.HashMap[String, String]()
          envs.foreach(f => envs4j.put(f._1, f._2))

          val batch = new ArrowPythonRunner(
            Seq(ChainedPythonFunctions(Seq(PythonFunction(
              code, envs4j, "python", pythonVersion)))), sourceSchema,
            timezoneID, runnerConf
          )

          val newIter = dataServers.map { irow =>
            encoder.toRow(Row.fromSeq(Seq(irow.host, irow.port, irow.timezone)))
          }.iterator
          val javaContext = new JavaContext()
          val commonTaskContext = new AppContextImpl(javaContext, batch)
          val columnarBatchIter = batch.compute(Iterator(newIter), 0, commonTaskContext)
          val data = columnarBatchIter.flatMap { batch =>
            batch.rowIterator.asScala.map(f => encoder.fromRow(f))
          }.toSeq
          javaContext.markComplete
          javaContext.close
          val rdd = session.sparkContext.makeRDD[Row](data)
          session.createDataFrame(rdd, sourceSchema)
      }

    } catch {
      case e: Exception =>
        recognizeError(e)
    }


  }

  def isLocalMaster(conf: Map[String, String]): Boolean = {
    //      val master = MLSQLConf.MLSQL_MASTER.readFrom(configReader).getOrElse("")
    val master = conf.getOrElse("spark.master", "")
    master == "local" || master.startsWith("local[")
  }

  /**
   *
   * Here we should give mlsql log server information to the conf which
   * will be configured by ArrowPythonRunner
   */
  private def configureLogConf() = {
    val context = ScriptSQLExec.context()
    val conf = context.execListener.sparkSession.sqlContext.getAllConfs
    val extraConfig = if (isLocalMaster(conf)) {
      Map[String, String]()
    } else {
      Map(PythonWorkerFactory.Tool.REDIRECT_IMPL -> "tech.mlsql.log.RedirectStreamsToSocketServer")
    }
    conf.filter(f => f._1.startsWith("spark.mlsql.log.driver")) ++
      Map(
        ScalaMethodMacros.str(PythonConf.PY_EXECUTE_USER) -> context.owner,
        "groupId" -> context.groupId
      ) ++ extraConfig
  }


  private def getSchemaAndConf(envSession: SetSession) = {
    def error = {
      throw new MLSQLException(
        """
          |Using `!python conf` to specify the python return value format is required.
          |Do like following:
          |
          |```
          |!python conf "schema=st(field(a,integer),field(b,integer))"
          |```
  """.stripMargin)
    }

    val runnerConf = envSession.fetchPythonRunnerConf match {
      case Some(conf) =>
        val temp = conf.collect().map(f => (f.k, f.v)).toMap
        if (!temp.contains("schema")) {
          error
        }
        temp
      case None => error
    }
    runnerConf
  }

  private def recognizeError(e: Exception) = {
    val buffer = ArrayBuffer[String]()
    format_full_exception(buffer, e, true)
    val typeError = buffer.filter(f => f.contains("Previous exception in task: null")).filter(_.contains("org.apache.spark.sql.vectorized.ArrowColumnVector$ArrowVectorAccessor")).size > 0
    if (typeError) {
      throw new MLSQLException(
        """
          |We can not reconstruct data from Python.
          |Try to use !python conf "schema=" change your schema.
  """.stripMargin, e)
    }
    throw e
  }


  override def supportedVersions: Seq[String] = {
    Seq("1.5.0-SNAPSHOT", "1.5.0")
  }


  override def doc: Doc = Doc(MarkDownDoc,
    s""" """.stripMargin)


  override def codeExample: Code = Code(SQLCode,
    s""" """.stripMargin)

  override def batchPredict(df: DataFrame, path: String, params: Map[String, String]): DataFrame = train(df, path, params)

  override def load(sparkSession: SparkSession, path: String, params: Map[String, String]): Any = ???

  override def predict(sparkSession: SparkSession, _model: Any, name: String, params: Map[String, String]): UserDefinedFunction = ???

}





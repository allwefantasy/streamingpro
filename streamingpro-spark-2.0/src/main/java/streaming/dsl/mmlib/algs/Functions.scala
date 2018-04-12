package streaming.dsl.mmlib.algs

import java.io.{ByteArrayOutputStream, File}
import java.util.{Collections, Properties, Random}

import net.csdn.common.logging.Loggers
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.Partitioner
import org.apache.spark.ml.linalg.SQLDataTypes._
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.ml.param.Params
import org.apache.spark.ml.util.{MLReadable, MLWritable}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession, functions => F}
import org.apache.spark.util.{ExternalCommandRunner, ObjPickle, WowMD5, WowXORShiftRandom}
import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.nn.conf.MultiLayerConfiguration
import org.deeplearning4j.optimize.api.IterationListener
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.deeplearning4j.spark.api.RDDTrainingApproach
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingMaster
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration
import streaming.common.HDFSOperator

import scala.collection.mutable.ArrayBuffer

/**
  * Created by allwefantasy on 13/1/2018.
  */
trait Functions {
  val logger = Loggers.getLogger(getClass)

  def sampleUnbalanceWithMultiModel(df: DataFrame, path: String, params: Map[String, String], train: (DataFrame, Int) => Unit) = {
    //select count(*) as subLabelCount,label from _ group by labelCol order by  subLabelCount asc
    val labelCol = params.getOrElse("labelCol", "label")
    val labelToCountSeq = df.groupBy(labelCol).agg(F.count(labelCol).as("subLabelCount")).orderBy(F.asc("subLabelCount")).
      select(labelCol, "subLabelCount").collect().map { f =>
      (f.getDouble(0), f.getLong(1))
    }
    val forLog = labelToCountSeq.map(f => s"${f._1}:${f._2}").mkString(",")
    logger.info(s"computing data stat:${forLog}")
    val labelCount = labelToCountSeq.size

    val dfWithLabelPartition = df.rdd.map { f =>
      (f.getAs[Double](labelCol).toInt, f)
    }.partitionBy(new Partitioner {
      override def numPartitions: Int = labelCount

      override def getPartition(key: Any): Int = {
        key.asInstanceOf[Int]
      }
    }).cache()

    try {
      val minLabel = labelToCountSeq.head._1
      val minCount = labelToCountSeq.head._2

      val maxLabel = labelToCountSeq.last._1
      val maxCount = labelToCountSeq.last._2

      val times = (maxCount.toDouble / minCount).ceil

      val labelToCountMapBr = df.sparkSession.sparkContext.broadcast(labelToCountSeq.map { f =>
        //sample rate
        (f._1, minCount.toDouble / f._2)
      }.toMap)
      val forLog2 = labelToCountMapBr.value.map(f => s"${f._1}:${f._2}").mkString(",")
      logger.info(s"all label sample rate:${forLog2}")

      (0 until times.toInt).foreach { time =>
        val tempRdd = dfWithLabelPartition.mapPartitionsWithIndex { (label, iter) =>
          val wow = new WowXORShiftRandom()
          iter.filter(k => wow.nextDouble <= labelToCountMapBr.value(label)).map(f => f._2)
        }
        val trainData = df.sparkSession.createDataFrame(tempRdd, df.schema)
        logger.info(s"training model :${time}")
        train(trainData, time)
      }
    } finally {
      dfWithLabelPartition.unpersist(false)
    }
  }

  def configureModel(model: Params, params: Map[String, String]) = {
    model.params.map { f =>
      if (params.contains(f.name)) {
        val v = params(f.name)
        val m = model.getClass.getMethods.filter(m => m.getName == s"set${f.name.capitalize}").head
        val pt = m.getParameterTypes.head
        val v2 = pt match {
          case i if i.isAssignableFrom(classOf[Int]) => v.toInt
          case i if i.isAssignableFrom(classOf[Double]) => v.toDouble
          case i if i.isAssignableFrom(classOf[Float]) => v.toFloat
          case i if i.isAssignableFrom(classOf[Boolean]) => v.toBoolean
          case _ => v
        }
        m.invoke(model, v2.asInstanceOf[AnyRef])
      }
    }
  }

  def mapParams(name: String, params: Map[String, String]) = {
    params.filter(f => f._1.startsWith(name + ".")).map(f => (f._1.split("\\.").drop(1).mkString("."), f._2))
  }

  def arrayParams(name: String, params: Map[String, String]) = {
    params.filter(f => f._1.startsWith(name + ".")).map { f =>
      val Array(name, group, key) = f._1.split("\\.")
      (group, key, f._2)
    }.groupBy(f => f._1).map { f => f._2.map(k =>
      (k._2, k._3)).toMap
    }.toArray
  }

  def getModelConstructField(model: Any, modelName: String, fieldName: String) = {
    val modelField = model.getClass.getDeclaredField("org$apache$spark$ml$feature$" + modelName + "$$" + fieldName)
    modelField.setAccessible(true)
    modelField.get(model)
  }

  def getModelField(model: Any, fieldName: String) = {
    val modelField = model.getClass.getDeclaredField(fieldName)
    modelField.setAccessible(true)
    modelField.get(model)
  }

  def loadModels(path: String, modelType: (String) => Any) = {
    val files = HDFSOperator.listModelDirectory(path).filterNot(_.getPath.getName.startsWith("__"))
    val models = ArrayBuffer[Any]()
    files.foreach { f =>
      val model = modelType(f.getPath.toString)
      models += model
    }
    models
  }

  def trainModels[T <: Model[T]](df: DataFrame, path: String, params: Map[String, String], modelType: () => Params) = {

    def f(trainData: DataFrame, modelIndex: Int) = {
      val alg = modelType()
      configureModel(alg, params)
      val model = alg.asInstanceOf[Estimator[T]].fit(trainData)
      model.asInstanceOf[MLWritable].write.overwrite().save(path + "/" + modelIndex)
    }
    params.getOrElse("multiModels", "false").toBoolean match {
      case true => sampleUnbalanceWithMultiModel(df, path, params, f)
      case false =>
        f(df, 0)
    }
  }

  def predict_classification(sparkSession: SparkSession, _model: Any, name: String) = {

    val models = sparkSession.sparkContext.broadcast(_model.asInstanceOf[ArrayBuffer[Any]])

    val raw2probabilityMethod = if (sparkSession.version.startsWith("2.3")) "raw2probabilityInPlace" else "raw2probability"

    val f = (vec: Vector) => {
      models.value.map { model =>
        val predictRaw = model.getClass.getMethod("predictRaw", classOf[Vector]).invoke(model, vec).asInstanceOf[Vector]
        val raw2probability = model.getClass.getMethod(raw2probabilityMethod, classOf[Vector]).invoke(model, predictRaw).asInstanceOf[Vector]
        //model.getClass.getMethod("probability2prediction", classOf[Vector]).invoke(model, raw2probability).asInstanceOf[Vector]
        //概率，分类
        (raw2probability(raw2probability.argmax), raw2probability)
      }.sortBy(f => f._1).reverse.head._2
    }

    val f2 = (vec: Vector) => {
      models.value.map { model =>
        val predictRaw = model.getClass.getMethod("predictRaw", classOf[Vector]).invoke(model, vec).asInstanceOf[Vector]
        val raw2probability = model.getClass.getMethod(raw2probabilityMethod, classOf[Vector]).invoke(model, predictRaw).asInstanceOf[Vector]
        //model.getClass.getMethod("probability2prediction", classOf[Vector]).invoke(model, raw2probability).asInstanceOf[Vector]
        raw2probability
      }
    }

    sparkSession.udf.register(name + "_raw", f2)

    UserDefinedFunction(f, VectorType, Some(Seq(VectorType)))
  }

  def writeKafka(df: DataFrame, path: String, params: Map[String, String]) = {
    var kafkaParam = mapParams("kafkaParam", params)
    // we use pickler to write row to Kafka
    val structType = df.schema

    val newRDD = df.rdd.mapPartitions { iter =>
      ObjPickle.pickle(iter, structType)
    }
    val topic = kafkaParam("topic") + "_" + System.currentTimeMillis()
    if (!kafkaParam.getOrElse("reuse", "false").toBoolean) {
      kafkaParam += ("topic" -> topic)
      newRDD.foreachPartition { p =>
        val props = new Properties()
        kafkaParam.foreach(f => props.put(f._1, f._2))
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
        val producer = new KafkaProducer[String, Array[Byte]](props)
        try {
          p.foreach { row =>
            producer.send(new ProducerRecord[String, Array[Byte]](topic, row))
          }

          def pickle(msg: String) = {
            val out = new ByteArrayOutputStream()
            ObjPickle.pickle(msg, out)
            val stopMsg = out.toByteArray
            out.close()
            stopMsg
          }

          val stopMsg = pickle("_stop_")
          producer.send(new ProducerRecord[String, Array[Byte]](kafkaParam("topic"), stopMsg))
        } finally {
          producer.close()
        }

      }
    }
    (kafkaParam, newRDD)
  }

  def copyToHDFS(tempModelLocalPath: String, path: String, clean: Boolean) = {
    val fs = FileSystem.get(new Configuration())
    fs.delete(new Path(path), true)
    fs.copyFromLocalFile(new Path(tempModelLocalPath),
      new Path(path))
    FileUtils.forceDelete(new File(tempModelLocalPath))
  }

  def createTempModelLocalPath(path: String, autoCreateParentDir: Boolean = true) = {
    val dir = "/tmp/train/" + WowMD5.md5Hash(path)
    if (autoCreateParentDir) {
      FileUtils.forceMkdir(new File(dir))
    }
    dir
  }

  def dl4jClassificationTrain(df: DataFrame, path: String, params: Map[String, String], multiLayerConfiguration: () => MultiLayerConfiguration): Unit = {
    require(params.contains("featureSize"), "featureSize is required")

    val labelSize = params.getOrElse("labelSize", "-1").toInt
    val batchSize = params.getOrElse("batchSize", "32").toInt

    val epochs = params.getOrElse("epochs", "1").toInt
    val validateTable = params.getOrElse("validateTable", "")

    val tm = SQLDL4J.init2(df.sparkSession.sparkContext.isLocal, batchSizePerWorker = batchSize)

    val netConf = multiLayerConfiguration()

    val sparkNetwork = new SparkDl4jMultiLayer(df.sparkSession.sparkContext, netConf, tm)
    sparkNetwork.setCollectTrainingStats(false)
    sparkNetwork.setListeners(Collections.singletonList[IterationListener](new ScoreIterationListener(1)))

    val labelFieldName = params.getOrElse("outputCol", "label")
    val newDataSetRDD = if (df.schema.fieldNames.contains(labelFieldName)) {

      require(params.contains("labelSize"), "labelSize is required")

      df.select(params.getOrElse("inputCol", "features"), params.getOrElse("outputCol", "label")).rdd.map { row =>
        val features = row.getAs[Vector](0)
        val label = row.getAs[Vector](1)
        new org.nd4j.linalg.dataset.DataSet(Nd4j.create(features.toArray), Nd4j.create(label.toArray))
      }.toJavaRDD()
    } else {
      df.select(params.getOrElse("inputCol", "features")).rdd.map { row =>
        val features = row.getAs[Vector](0)
        new org.nd4j.linalg.dataset.DataSet(Nd4j.create(features.toArray), Nd4j.zeros(0))
      }.toJavaRDD()
    }


    (0 until epochs).foreach { i =>
      sparkNetwork.fit(newDataSetRDD)
    }

    val tempModelLocalPath = createTempModelLocalPath(path)
    ModelSerializer.writeModel(sparkNetwork.getNetwork, new File(tempModelLocalPath, "dl4j.model"), true)
    copyToHDFS(tempModelLocalPath + "/dl4j.model", path + "/dl4j.model", true)

    if (!validateTable.isEmpty) {

      val testDataSetRDD = df.sparkSession.table(validateTable).select(params.getOrElse("inputCol", "features"), params.getOrElse("outputCol", "label")).rdd.map { row =>
        val features = row.getAs[Vector](0)
        val label = row.getAs[Vector](1)
        new org.nd4j.linalg.dataset.DataSet(Nd4j.create(features.toArray), Nd4j.create(label.toArray))
      }.toJavaRDD()

      val evaluation = sparkNetwork.doEvaluation(testDataSetRDD, batchSize, new Evaluation(labelSize))(0); //Work-around for 0.9.1 bug: see https://deeplearning4j.org/releasenotes
      logger.info("***** Evaluation *****")
      logger.info(evaluation.stats())
      logger.info("***** Example Complete *****")
    }
    tm.deleteTempFiles(df.sparkSession.sparkContext)
  }

}

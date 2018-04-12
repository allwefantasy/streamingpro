package streaming.dsl.mmlib.algs

import java.io.File
import java.nio.file.{Files, Paths}
import java.util

import org.apache.commons.io.FileUtils
import org.apache.spark.TaskContext
import org.apache.spark.api.python.WowPythonRunner
import org.apache.spark.ml.linalg.SQLDataTypes._
import org.apache.spark.ml.linalg.{DenseVector, SparseVector, VectorUDT, Vectors}
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow
import org.apache.spark.sql.{DataFrame, Row, SaveMode, SparkSession}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.types._
import org.apache.spark.util.{ExternalCommandRunner, VectorSerDer}
import streaming.dsl.mmlib.SQLAlg
import org.apache.spark.util.ObjPickle._
import org.apache.spark.util.VectorSerDer._

import scala.collection.JavaConverters._
import scala.io.Source

/**
  * Created by allwefantasy on 5/2/2018.
  */
class SQLSKLearn extends SQLAlg with Functions {
  override def train(df: DataFrame, path: String, params: Map[String, String]): Unit = {
    val (kafkaParam, newRDD) = writeKafka(df, path, params)
    val systemParam = mapParams("systemParam", params)

    val stopFlagNum = newRDD.getNumPartitions

    val fitParam = arrayParams("fitParam", params)
    val fitParamRDD = df.sparkSession.sparkContext.parallelize(fitParam, fitParam.length)
    val pythonPath = systemParam.getOrElse("pythonPath", "python")
    val pythonVer = systemParam.getOrElse("pythonVer", "2.7")
    val wowRDD = fitParamRDD.map { f =>
      val paramMap = new util.HashMap[String, Object]()
      var item = f.asJava
      if (!f.contains("modelPath")) {
        item = (f + ("modelPath" -> path)).asJava
      }

      val alg = f("alg")
      val sk_bayes = Source.fromInputStream(ExternalCommandRunner.getClass.getResourceAsStream(s"/python/mlsql_sk_${alg}.py")).
        getLines().mkString("\n")
      val userFileName = s"mlsql_sk_${alg}.py"

      val tempModelLocalPath = "/tmp/" + System.currentTimeMillis()

      paramMap.put("fitParam", item)
      paramMap.put("kafkaParam", kafkaParam.asJava)
      paramMap.put("internalSystemParam", Map(
        "stopFlagNum" -> stopFlagNum,
        "tempModelLocalPath" -> tempModelLocalPath
      ).asJava)
      paramMap.put("systemParam", systemParam.asJava)



      val res = ExternalCommandRunner.run(Seq(pythonPath, userFileName),
        paramMap,
        MapType(StringType, MapType(StringType, StringType)),
        sk_bayes,
        userFileName, modelPath = path
      )
      res.foreach(f => f)
      //读取模型文件，保存到hdfs上，方便下次获取
      val file = new File(new File(tempModelLocalPath), "model.pickle")
      val byteArray = Files.readAllBytes(Paths.get(file.getPath))
      FileUtils.deleteDirectory(new File(tempModelLocalPath))
      Row.fromSeq(Seq(byteArray))
    }
    df.sparkSession.createDataFrame(wowRDD, StructType(Seq(StructField("bytes", BinaryType)))).
      write.
      mode(SaveMode.Overwrite).
      parquet(new File(path, "0").getPath)

    val tempRDD = df.sparkSession.sparkContext.parallelize(Seq(Map("pythonPath" -> pythonPath, "pythonVer" -> pythonVer)), 1).map { f =>
      Row.fromSeq(Seq(f))
    }
    df.sparkSession.createDataFrame(tempRDD, StructType(Seq(StructField("systemParam", MapType(StringType, StringType))))).
      write.
      mode(SaveMode.Overwrite).
      parquet(new File(path, "__meta__").getPath)

  }

  override def load(sparkSession: SparkSession, path: String): Any = {
    val models = sparkSession.read.parquet(new File(path, "0").getPath).collect().map(f => f.get(0).asInstanceOf[Array[Byte]]).toSeq
    val metas = sparkSession.read.parquet(new File(path, "__meta__").getPath).collect().map(f => f.get(0).asInstanceOf[Map[String, String]]).toSeq
    (models, metas)
  }

  override def predict(sparkSession: SparkSession, _model: Any, name: String): UserDefinedFunction = {
    val (modelsTemp, metasTemp) = _model.asInstanceOf[(Seq[Array[Byte]], Seq[Map[String, String]])]
    val models = sparkSession.sparkContext.broadcast(modelsTemp)

    val pythonPath = metasTemp(0)("pythonPath")
    val pythonVer = metasTemp(0)("pythonVer")

    val sk_bayes = Source.fromInputStream(ExternalCommandRunner.getClass.getResourceAsStream("/python/sk_predict.py")).
      getLines().mkString("\n")

    val userFileName = "sk_predict.py"

    val maps = new util.HashMap[String, java.util.Map[String, String]]()
    val item = new util.HashMap[String, String]()
    item.put("funcPath", "/tmp/" + System.currentTimeMillis())
    maps.put("systemParam", item)
    //driver 节点执行
    val res = ExternalCommandRunner.run(Seq(pythonPath, userFileName),
      maps,
      MapType(StringType, MapType(StringType, StringType)),
      sk_bayes,
      userFileName, modelPath = null
    )
    res.foreach(f => f)
    val command = Files.readAllBytes(Paths.get(item.get("funcPath")))

    val f = (v: org.apache.spark.ml.linalg.Vector, model: Array[Byte]) => {
      val modelRow = InternalRow.fromSeq(Seq(model))
      val v_ser = pickleInternalRow(Seq(ser_vector(v)).toIterator, vector_schema())
      val v_ser2 = pickleInternalRow(Seq(modelRow).toIterator, StructType(Seq(StructField("model", BinaryType))))
      val v_ser3 = v_ser ++ v_ser2
      val iter = WowPythonRunner.run(pythonPath, pythonVer, command, v_ser3, TaskContext.get().partitionId(), model)
      val a = iter.next()
      VectorSerDer.deser_vector(unpickle(a).asInstanceOf[java.util.ArrayList[Object]].get(0))
    }

    val f2 = (v: org.apache.spark.ml.linalg.Vector) => {
      models.value.map { model =>
        val resV = f(v, model)
        (resV(resV.argmax), resV)
      }.sortBy(f => f._1).reverse.head._2
    }

    UserDefinedFunction(f2, VectorType, Some(Seq(VectorType)))
  }
}

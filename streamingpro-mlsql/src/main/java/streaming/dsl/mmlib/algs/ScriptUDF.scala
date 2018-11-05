package streaming.dsl.mmlib.algs

import org.apache.spark.sql.catalyst.expressions.{Expression, ScalaUDF}
import org.apache.spark.sql.execution.aggregate.ScalaUDAF
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.udf.UDFManager
import streaming.dsl.mmlib.SQLAlg
import streaming.udf.{PythonSourceUDAF, PythonSourceUDF, ScalaSourceUDAF, ScalaSourceUDF}

/**
  * Created by allwefantasy on 27/8/2018.
  */
class ScriptUDF extends SQLAlg with MllibFunctions with Functions {

  override def skipPathPrefix: Boolean = true

  override def train(df: DataFrame, path: String, params: Map[String, String]): DataFrame = {
    emptyDataFrame()(df)
  }

  /*
      register ScalaScriptUDF.`scriptText` as udf1;
   */
  override def load(sparkSession: SparkSession, path: String, params: Map[String, String]): Any = {
    val res = params.get("code").getOrElse(sparkSession.table(path).head().getString(0))

    val lang = params.getOrElse("lang", "scala")
    val udfType = params.getOrElse("udfType", "udf")

    val udf = () => {
      val (func, returnType) = lang match {
        case "python" =>
          if (params.contains("className")) {
            PythonSourceUDF(res, params("className"), params.get("methodName"), params("dataType"))
          } else {
            PythonSourceUDF(res, params.get("methodName"), params("dataType"))
          }

        case _ =>
          if (params.contains("className")) {
            ScalaSourceUDF(res, params("className"), params.get("methodName"))
          } else {
            ScalaSourceUDF(res, params.get("methodName"))
          }
      }
      (e: Seq[Expression]) => ScalaUDF(func, returnType, e)
    }

    val udaf = () => {
      val func = lang match {
        case "python" =>
          PythonSourceUDAF(res, params("className"))

        case _ =>
          ScalaSourceUDAF(res, params("className"))
      }
      (e: Seq[Expression]) => ScalaUDAF(e, func)
    }

    udfType match {
      case "udaf" => udaf()
      case _ => udf()
    }
  }

  override def predict(sparkSession: SparkSession, _model: Any, name: String, params: Map[String, String]): UserDefinedFunction = {
    val func = _model.asInstanceOf[(Seq[Expression]) => ScalaUDF]
    UDFManager.register(sparkSession, name, func)
    null
  }
}

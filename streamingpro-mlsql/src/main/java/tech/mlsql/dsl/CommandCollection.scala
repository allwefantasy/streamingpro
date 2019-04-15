package tech.mlsql.dsl

import org.apache.spark.sql.SparkSession
import streaming.dsl.{ScriptSQLExec, ScriptSQLExecListener}

/**
  * 2019-04-11 WilliamZhu(allwefantasy@gmail.com)
  */
object CommandCollection {
  def fill(context: ScriptSQLExecListener): Unit = {
    context.addEnv("desc", """run command as ShowTableExt.`{}`""")
    context.addEnv("kill", """run command as Kill.`{}`""")
    context.addEnv("jdbc", """ run command as JDBC.`` where `driver-statement-0`='''{}''' """)
    context.addEnv("show",
      """
        |run command as ShowCommand.`{}/{}/{}/{}/{}/{}/{}/{}/{}/{}/{}/{}`
      """.stripMargin)
  }

  def evaluateMLSQL(spark: SparkSession, mlsql: String) = {
    val context = new ScriptSQLExecListener(spark, null, null)
    ScriptSQLExec.parse(mlsql, context, true, true)
    spark.table(context.getLastSelectTable().get)
  }
}

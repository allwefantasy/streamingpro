/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package streaming.dsl

import streaming.dsl.parser.DSLSQLParser._
import streaming.dsl.template.TemplateMerge

/**
  * Created by allwefantasy on 12/1/2018.
  */
class IncludeAdaptor(preProcessListener: PreProcessIncludeListener) extends DslAdaptor {

  def evaluate(value: String) = {
    TemplateMerge.merge(value, preProcessListener.env().toMap)
  }

  override def parse(ctx: SqlContext): Unit = {
    var functionName = ""
    var format = ""
    var path = ""
    var option = Map[String, String]()
    //    val owner = option.get("owner")
    (0 to ctx.getChildCount() - 1).foreach { tokenIndex =>

      ctx.getChild(tokenIndex) match {
        case s: FunctionNameContext =>
          functionName = s.getText
        case s: FormatContext =>
          format = s.getText
        case s: PathContext =>
          path = TemplateMerge.merge(cleanStr(s.getText), preProcessListener.env().toMap)
        case s: ExpressionContext =>
          option += (cleanStr(s.qualifiedName().getText) -> evaluate(getStrOrBlockStr(s)))
        case s: BooleanExpressionContext =>
          option += (cleanStr(s.expression().qualifiedName().getText) -> evaluate(getStrOrBlockStr(s.expression())))
        case _ =>
      }
    }
    val alg = IncludeAdaptor.findAlg(format, option)
    if (!alg.skipPathPrefix) {
      path = withPathPrefix(preProcessListener.pathPrefix(None), path)
    }

    val content = alg.fetchSource(preProcessListener.sparkSession, path, Map("format" -> format) ++ option)
    val originIncludeText = currentText(ctx)
    preProcessListener.addInclude(originIncludeText, content)
  }
}

object IncludeAdaptor {
  val mapping = Map[String, String](
    "hdfs" -> "streaming.dsl.mmlib.algs.includes.HDFSIncludeSource",
    "http" -> "streaming.dsl.mmlib.algs.includes.HTTPIncludeSource",

    "function" -> "streaming.dsl.mmlib.algs.includes.analyst.HttpBaseDirIncludeSource",
    "view" -> "streaming.dsl.mmlib.algs.includes.analyst.HttpBaseDirIncludeSource",
    "table" -> "streaming.dsl.mmlib.algs.includes.analyst.HttpBaseDirIncludeSource",
    "job" -> "streaming.dsl.mmlib.algs.includes.analyst.HttpBaseDirIncludeSource"
  )

  def findAlg(format: String, options: Map[String, String]) = {
    val clzz = mapping.getOrElse(format, options.getOrElse("implClass", "streaming.dsl.mmlib.algs.includes." + format))
    Class.forName(clzz).newInstance().asInstanceOf[IncludeSource]
  }

}


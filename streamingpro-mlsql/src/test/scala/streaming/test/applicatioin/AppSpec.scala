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

package streaming.test.applicatioin

import org.apache.spark.sql.AnalysisException
import org.apache.spark.streaming.BasicSparkOperation
import streaming.core.{BasicMLSQLConfig, SpecFunctions}

class AppSpec extends BasicSparkOperation with SpecFunctions with BasicMLSQLConfig {


  "streaming.mode.application.fails_all" should "work fine" in {
    println("set streaming.mode.application.fails_all true")
    intercept[AnalysisException] {
      appWithBatchContext(batchParams ++ Array(
        "-streaming.mode.application.fails_all", "true"
      ), "classpath:///test/batch-mlsql-error.json")
    }

    println("set streaming.mode.application.fails_all false but there are exception")
    appWithBatchContext(batchParams ++ Array(
      "-streaming.mode.application.fails_all", "false"
    ), "classpath:///test/batch-mlsql-error.json")

    println("set streaming.mode.application.fails_all true but there are no exception")
    appWithBatchContext(batchParams ++ Array(
      "-streaming.mode.application.fails_all", "true"
    ), "classpath:///test/batch-mlsql.json")

  }

}

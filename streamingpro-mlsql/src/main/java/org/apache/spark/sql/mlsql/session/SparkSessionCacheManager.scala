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

package org.apache.spark.sql.mlsql.session

/**
  * Created by allwefantasy on 3/6/2018.
  */

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.spark.sql.SparkSession
import streaming.log.Logging

import scala.collection.JavaConverters._


class SparkSessionCacheManager() extends Logging {
  private val cacheManager =
    Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder()
        .setDaemon(true).setNameFormat(getClass.getSimpleName + "-%d").build())

  private[this] val userToSparkSession =
    new ConcurrentHashMap[String, (SparkSession, AtomicInteger)]

  private[this] val userLatestLogout = new ConcurrentHashMap[String, Long]
  private[this] val userLatestVisit = new ConcurrentHashMap[String, Long]
  private[this] val idleTimeout = 60 * 1000

  def set(user: String, sparkSession: SparkSession): Unit = {
    userToSparkSession.put(user, (sparkSession, new AtomicInteger(1)))
    userLatestVisit.put(user, System.currentTimeMillis())
  }

  def getAndIncrease(user: String): Option[SparkSession] = {
    Some(userToSparkSession.get(user)) match {
      case Some((ss, times)) if !ss.sparkContext.isStopped =>
        log.info(s"SparkSession for [$user] is reused for ${times.incrementAndGet()} times.")
        Some(ss)
      case _ =>
        log.info(s"SparkSession for [$user] isn't cached, will create a new one.")
        None
    }
  }

  def decrease(user: String): Unit = {
    Some(userToSparkSession.get(user)) match {
      case Some((ss, times)) if !ss.sparkContext.isStopped =>
        userLatestLogout.put(user, System.currentTimeMillis())
        log.info(s"SparkSession for [$user] is reused for ${times.decrementAndGet()} times.")
      case _ =>
        log.warn(s"SparkSession for [$user] was not found in the cache.")
    }
  }

  def visit(user: String): Unit = {
    userLatestVisit.put(user, System.currentTimeMillis())
  }

  private[this] def removeSparkSession(user: String): Unit = {
    userToSparkSession.remove(user)
  }

  private[this] val sessionCleaner = new Runnable {
    override def run(): Unit = {
      userToSparkSession.asScala.foreach {
        case (user, (_, times)) if times.get() > 0 => {
          if (userLatestVisit.getOrDefault(user, Long.MaxValue) + SparkSessionCacheManager.getExpireTimeout
            > System.currentTimeMillis()) {
            log.debug(s"There are $times active connection(s) bound to the SparkSession instance" +
              s" of $user ")
          } else {
            SparkSessionCacheManager.getSessionManager.closeSession(SessionIdentifier(user))
          }
        }
        case (user, (_, _)) if !userLatestLogout.containsKey(user) =>
        case (user, (session, _))
          if userLatestLogout.get(user) + idleTimeout <= System.currentTimeMillis() =>
          log.info(s"Stopping idle SparkSession for user [$user].")
          removeSparkSession(user)
        case _ =>
      }
    }
  }

  /**
    * Periodically close idle SparkSessions in 'spark.kyuubi.session.clean.interval(default 5min)'
    */
  def start(): Unit = {
    // at least 1 minutes
    log.info(s"Scheduling SparkSession cache cleaning every 60 seconds")
    cacheManager.scheduleAtFixedRate(sessionCleaner, 60, 60, TimeUnit.SECONDS)
  }

  def stop(): Unit = {
    log.info("Stopping SparkSession Cache Manager")
    cacheManager.shutdown()
    userToSparkSession.asScala.values.foreach { kv => kv._1.stop() }
    userToSparkSession.clear()
  }
}

object SparkSessionCacheManager {

  type Usage = AtomicInteger

  private[this] var sparkSessionCacheManager: SparkSessionCacheManager = _

  private[this] var sessionManager: SessionManager = _

  private[this] val EXPIRE_SMALL_TIMEOUT = 1 * 60 * 60 * 1000L
  private[this] var expireTimeout = EXPIRE_SMALL_TIMEOUT

  def startCacheManager(): Unit = {
    sparkSessionCacheManager = new SparkSessionCacheManager()
    sparkSessionCacheManager.start()
  }

  def setSessionManager(manager: SessionManager): Unit = {
    sessionManager = manager
  }

  def getSessionManager: SessionManager = sessionManager

  def getSessionManagerOption: Option[SessionManager] = if (sessionManager == null) None else Some(sessionManager)


  def setExpireTimeout(expire: Long): String = {
    if (expire > EXPIRE_SMALL_TIMEOUT) {
      expireTimeout = expire
      s"set session expire success $expire"
    } else {
      s"session expire must bigger than $EXPIRE_SMALL_TIMEOUT ,current is $expire"
    }
  }

  def getExpireTimeout: Long = expireTimeout

  def get: SparkSessionCacheManager = sparkSessionCacheManager
}

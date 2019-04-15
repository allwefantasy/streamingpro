package tech.mlsql.job

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.mlsql.session.{SessionIdentifier, SparkSessionCacheManager}
import streaming.log.{Logging, WowLog}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

/**
  * 2019-04-07 WilliamZhu(allwefantasy@gmail.com)
  */
object JobManager extends Logging {
  private[this] var _jobManager: JobManager = _
  private[this] val _executor = Executors.newFixedThreadPool(100)

  def shutdown = {
    logInfo(s"JobManager is shutdown....")
    _executor.shutdownNow()
    _jobManager.shutdown
    _jobManager = null
  }

  def init(spark: SparkSession, initialDelay: Long = 30, checkTimeInterval: Long = 5) = {
    synchronized {
      if (_jobManager == null) {
        logInfo(s"JobManager started with initialDelay=${initialDelay} checkTimeInterval=${checkTimeInterval}")
        _jobManager = new JobManager(spark, initialDelay, checkTimeInterval)
        _jobManager.run
      }
    }
  }

  def initForTest(spark: SparkSession, initialDelay: Long = 30, checkTimeInterval: Long = 5) = {
    if (_jobManager == null) {
      logInfo(s"JobManager started with initialDelay=${initialDelay} checkTimeInterval=${checkTimeInterval}")
      _jobManager = new JobManager(spark, initialDelay, checkTimeInterval)
    }
  }

  def run(session: SparkSession, job: MLSQLJobInfo, f: () => Unit): Unit = {
    try {
      if (_jobManager == null) {
        f()
      } else {
        session.sparkContext.setJobGroup(job.groupId, job.jobName, true)
        _jobManager.groupIdToMLSQLJobInfo.put(job.groupId, job)
        f()
      }
    } finally {
      handleJobDone(job.groupId)
      session.sparkContext.clearJobGroup()
    }
  }

  def asyncRun(session: SparkSession, job: MLSQLJobInfo, f: () => Unit) = {
    // TODO: (fchen) 改成callback
    _executor.execute(new Runnable {
      override def run(): Unit = {
        try {
          _jobManager.groupIdToMLSQLJobInfo.put(job.groupId, job)
          session.sparkContext.setJobGroup(job.groupId, job.jobName, true)
          f()
        } finally {
          handleJobDone(job.groupId)
          session.sparkContext.clearJobGroup()
        }
      }
    })
  }

  def getJobInfo(owner: String,
                 jobType: String,
                 jobName: String,
                 jobContent: String,
                 timeout: Long): MLSQLJobInfo = {
    val startTime = System.currentTimeMillis()
    val groupId = _jobManager.nextGroupId.incrementAndGet().toString
    MLSQLJobInfo(owner, jobType, jobName, jobContent, groupId, startTime, timeout)
  }

  def getJobInfo: Map[String, MLSQLJobInfo] =
    _jobManager.groupIdToMLSQLJobInfo.asScala.toMap

  def addJobManually(job: MLSQLJobInfo) = {
    _jobManager.groupIdToMLSQLJobInfo.put(job.groupId, job)
  }

  def removeJobManually(groupId: String) = {
    handleJobDone(groupId)
  }

  def killJob(session: SparkSession, groupId: String): Unit = {
    _jobManager.cancelJobGroup(session, groupId)
  }

  private def handleJobDone(groupId: String): Unit = {
    _jobManager.groupIdToMLSQLJobInfo.remove(groupId)

  }
}

class JobManager(_spark: SparkSession, initialDelay: Long, checkTimeInterval: Long) extends Logging with WowLog {
  val groupIdToMLSQLJobInfo = new ConcurrentHashMap[String, MLSQLJobInfo]()
  val nextGroupId = new AtomicInteger(0)

  val executor = Executors.newSingleThreadScheduledExecutor()

  def run = {
    executor.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = {
        groupIdToMLSQLJobInfo.foreach { f =>
          val elapseTime = System.currentTimeMillis() - f._2.startTime
          if (f._2.timeout > 0 && elapseTime >= f._2.timeout) {
            val tempSession = SparkSessionCacheManager.getSessionManagerOption match {
              case Some(sessionManager) =>
                sessionManager.getSessionOption(SessionIdentifier(f._2.owner))
              case None => None
            }
            val session = tempSession.map(f => f.sparkSession).getOrElse(_spark)
            cancelJobGroup(session, f._1)
          }
        }
      }
    }, initialDelay, checkTimeInterval, TimeUnit.SECONDS)
  }

  def cancelJobGroup(spark: SparkSession, groupId: String): Unit = {
    logInfo(format("JobManager Timer cancel job group " + groupId))
    val job = groupIdToMLSQLJobInfo.get(groupId)
    if (job != null && job.jobType == MLSQLJobType.STREAM) {
      spark.streams.active.filter(f => f.id.toString == job.groupId).map(f => f.runId.toString).headOption match {
        case Some(_) => spark.streams.get(job.groupId).stop()
        case None => logWarning(format(s"the stream job: ${job.groupId}, name:${job.jobName} is not in spark.streams."))
      }

    } else {
      spark.sparkContext.cancelJobGroup(groupId)
    }
    groupIdToMLSQLJobInfo.remove(groupId)
  }

  def shutdown = {
    executor.shutdownNow()
  }
}

case object MLSQLJobType {
  val SCRIPT = "script"
  val SQL = "sql"
  val STREAM = "stream"
}

case class MLSQLJobInfo(
                         owner: String,
                         jobType: String,
                         jobName: String,
                         jobContent: String,
                         groupId: String,
                         startTime: Long,
                         timeout: Long
                       )

package org.apache.spark

import org.apache.spark.sql.{MLSQLUtils, SparkSession}
import tech.mlsql.render.protocal.{MLSQLResourceRender, MLSQLScriptJob, MLSQLScriptJobGroup, MLSQLShufflePerfRender}

/**
  * 2019-01-28 WilliamZhu(allwefantasy@gmail.com)
  */
class MLSQLResource(spark: SparkSession, owner: String, getGroupId: String => String) {
  def resourceSummary(jobGroupId: String) = {
    val store = MLSQLUtils.getExecutorAllocationManager(spark)
    val listener = MLSQLUtils.getAppStatusStore(spark)
    val executorList = store.executorToTaskSummary.values.toSeq
    val activeJobs = listener.activeJobs

    val finalJobGroupId = getGroupId(jobGroupId)

    def getNumActiveTaskByJob(stageIds: Set[Int]) = {
      val (activeStages, completedStages, failedStages) = fetchStageByJob(stageIds)
      activeStages.map(f => listener.stageIdToData(f.stageId, f.attemptId).numActiveTasks).sum
    }

    def getDiskBytesSpilled(stageIds: Set[Int]) = {
      val (activeStages, completedStages, failedStages) = fetchStageByJob(stageIds)
      activeStages.map(f => listener.stageIdToData(f.stageId, f.attemptId).diskBytesSpilled).sum
    }

    def getInputRecords(stageIds: Set[Int]) = {
      val (activeStages, completedStages, failedStages) = fetchStageByJob(stageIds)
      activeStages.map(f => listener.stageIdToData(f.stageId, f.attemptId).inputRecords).sum
    }

    def getMemoryBytesSpilled(stageIds: Set[Int]) = {
      val (activeStages, completedStages, failedStages) = fetchStageByJob(stageIds)
      activeStages.map(f => listener.stageIdToData(f.stageId, f.attemptId).memoryBytesSpilled).sum
    }

    val currentJobGroupActiveTasks = if (jobGroupId == null) activeJobs.map { f =>
      getNumActiveTaskByJob(f._2.stageIds.toSet)
    }.sum
    else activeJobs.filter(f => f._2.jobGroup.get == finalJobGroupId).map { f =>
      getNumActiveTaskByJob(f._2.stageIds.toSet)
    }.sum


    val currentDiskBytesSpilled = if (jobGroupId == null) activeJobs.map(f => getDiskBytesSpilled(f._2.stageIds.toSet)).sum
    else activeJobs.filter(f => f._2.jobGroup.get == finalJobGroupId).map { f =>
      getDiskBytesSpilled(f._2.stageIds.toSet)
    }.sum

    val currentInputRecords = if (jobGroupId == null) activeJobs.map { f =>
      getInputRecords(f._2.stageIds.toSet)
    }.sum
    else activeJobs.filter(f => f._2.jobGroup.get == finalJobGroupId).map { f =>
      getInputRecords(f._2.stageIds.toSet)
    }.sum

    val currentMemoryBytesSpilled = if (jobGroupId == null) activeJobs.map { f =>
      getMemoryBytesSpilled(f._2.stageIds.toSet)
    }.sum
    else activeJobs.filter(f => f._2.jobGroup.get == finalJobGroupId).map { f =>
      getMemoryBytesSpilled(f._2.stageIds.toSet)
    }.sum

    val shuffle = MLSQLShufflePerfRender(memoryBytesSpilled = currentMemoryBytesSpilled, diskBytesSpilled = currentDiskBytesSpilled, inputRecords = currentInputRecords)


    MLSQLResourceRender(
      currentJobGroupActiveTasks = currentJobGroupActiveTasks,
      activeTasks = executorList.map(_.tasksActive).sum,
      failedTasks = executorList.map(_.tasksFailed).sum,
      completedTasks = executorList.map(_.tasksComplete).sum,
      totalTasks = executorList.map(_.tasksMax).sum,
      taskTime = executorList.map(_.duration).sum,
      gcTime = executorList.map(_.jvmGCTime).sum,
      activeExecutorNum = executorList.size,
      totalExecutorNum = listener.executorIdToBlockManagerId.size,
      totalCores = executorList.map(_.totalCores).sum,
      usedMemory = -1,
      totalMemory = -1,
      shuffleData = shuffle
    )


  }

  def fetchStageByJob(stageIds: Set[Int]) = {
    val listener = MLSQLUtils.getAppStatusStore(spark)
    val activeStages = listener.activeStages.values.filter(f => stageIds.contains(f.stageId)).toSeq
    val completedStages = listener.completedStages.filter(f => stageIds.contains(f.stageId)).reverse
    val failedStages = listener.failedStages.filter(f => stageIds.contains(f.stageId)).reverse
    (activeStages, completedStages, failedStages)
  }

  def jobDetail(jobGroupId: String) = {
    val listener = MLSQLUtils.getAppStatusStore(spark)
    val startTime = listener.startTime
    val endTime = listener.endTime

    val finalJobGroupId = getGroupId(jobGroupId)

    val activeJobs = listener.activeJobs.values.toSeq
    val completedJobs = listener.completedJobs.reverse
    val failedJobs = listener.failedJobs.reverse


    val mlsqlActiveJobs = activeJobs.map { f =>


      val (activeStages, completedStages, failedStages) = fetchStageByJob(f.stageIds.toSet)

      val activeTasks = activeStages.map(f => listener.stageIdToData(f.stageId, f.attemptId).numActiveTasks).sum

      MLSQLScriptJob(
        f.jobId,
        f.submissionTime.map(date => new java.sql.Date(date)),
        f.completionTime.map(date => new java.sql.Date(date)),
        f.numTasks,
        f.numActiveTasks,
        f.numCompletedTasks,
        f.numSkippedTasks,
        f.numFailedTasks,
        0,
        0,
        f.numActiveStages,
        0,
        f.numSkippedStages,
        f.numFailedStages
      )
    }
    MLSQLScriptJobGroup(
      jobGroupId, activeJobs.size, completedJobs.size, failedJobs.size, mlsqlActiveJobs
    )
  }
}

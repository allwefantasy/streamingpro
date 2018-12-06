package tech.mlsql.cluster.service

import tech.mlsql.cluster.service.BackendService.mapSResponseToObject

/**
  * 2018-12-04 WilliamZhu(allwefantasy@gmail.com)
  */
trait BackendStrategy {
  def invoke(backends: Seq[BackendCache]): Option[BackendCache]

}

class FirstBackendStrategy(tags: String) extends BackendStrategy {
  override def invoke(backends: Seq[BackendCache]): Option[BackendCache] = {
    val tagSet = tags.split(",").toSet
    if (tags.isEmpty) {
      backends.headOption
    } else {
      backends.filter(f => tagSet.intersect(f.meta.getTag.split(",").toSet).size > 0).headOption
    }

  }
}

class TaskLessBackendStrategy(tags: String) extends BackendStrategy {
  override def invoke(backends: Seq[BackendCache]): Option[BackendCache] = {
    val tagSet = tags.split(",").toSet
    var nonActiveBackend = BackendService.nonActiveBackend
    if (!tags.isEmpty) {
      nonActiveBackend = nonActiveBackend.filter(f => tagSet.intersect(f.getTag.split(",").toSet).size > 0)
    }
    val backend = if (nonActiveBackend.size > 0) {
      nonActiveBackend.headOption
    } else {
      var activeBackends = BackendService.activeBackend.toSeq
      if (!tags.isEmpty) {
        activeBackends = activeBackends.filter(f => tagSet.intersect(f._1.getTag.split(",").toSet).size > 0).sortBy(f => f._2)
      }
      activeBackends.headOption.map(f => f._1)

    }
    BackendService.find(backend)
  }
}

class FreeCoreBackendStrategy(tags: String) extends BackendStrategy {
  override def invoke(backends: Seq[BackendCache]): Option[BackendCache] = {

    val tagSet = tags.split(",").toSet

    var backends = BackendService.backends
    if (!tags.isEmpty) {
      backends = backends.filter(f => tagSet.intersect(f.meta.getTag.split(",").toSet).size > 0)
    }
    val backend = backends.seq.map { b =>
      val res = b.instance.instanceResource
      val resource = res.toBean[CSparkInstanceResource]().head
      (resource.totalCores - resource.totalTasks, b)
    }.sortBy(f => f._1).reverse.headOption.map(f => f._2.meta)

    BackendService.find(backend)
  }
}

case class CSparkInstanceResource(totalCores: Long, totalTasks: Long, totalUsedMemory: Long, totalMemory: Long)

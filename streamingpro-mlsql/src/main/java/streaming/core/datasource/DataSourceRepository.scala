package streaming.core.datasource

import java.io.File
import java.net.{URL, URLClassLoader, URLEncoder}
import java.nio.file.Files

import net.csdn.ServiceFramwork
import net.csdn.common.path.Url
import net.csdn.modules.http.RestRequest
import net.csdn.modules.transport.HttpTransportService
import net.sf.json.{JSONArray, JSONObject}
import org.apache.http.client.fluent.Request
import streaming.dsl.ScriptSQLExec

import scala.collection.JavaConverters._

/**
  * 2019-01-14 WilliamZhu(allwefantasy@gmail.com)
  */
class DataSourceRepository(url: String) {
  def httpClient = ServiceFramwork.injector.getInstance[HttpTransportService](classOf[HttpTransportService])

  //"http://respository.datasource.mlsql.tech"
  def getOrDefaultUrl = {
    if (url == null || url.isEmpty) {
      val context = ScriptSQLExec.contextGetOrForTest()
      require(context.userDefinedParam.contains("__datasource_repository_url__"), "context.__datasource_repository_url__ should be configure if you want use connect DataSourceRepository")
      context.userDefinedParam.get("__datasource_repository_url__")
    } else {
      url
    }
  }

  def listCommand = {
    val res = httpClient.http(new Url(s"${getOrDefaultUrl}/jar/manager/source/mapper"), "{}", RestRequest.Method.POST)

    JSONObject.fromObject(res.getContent).asScala.flatMap { kv =>
      kv._2.asInstanceOf[JSONArray].asScala.map { _item =>
        val item = _item.asInstanceOf[JSONObject]
        item.put("name", kv._1)
        val temp = new JSONArray()
        temp.add(item)
        val versionList = versionCommand(temp)
        val versionArray = new JSONArray
        versionList.foreach { v => versionArray.add(v) }
        item.put("versions", versionArray)
        item.toString
      }
    }.toSeq
  }

  def versionCommand(items: JSONArray) = {
    val request = new JSONArray
    items.asScala.map { _item =>
      val item = _item.asInstanceOf[JSONObject]
      val json = new JSONObject()
      json.put("jarname", item.getString("groupid") + "/" + item.getString("artifactId"))
      request.add(json)
    }

    val res = httpClient.http(new Url(s"${getOrDefaultUrl}/jar/manager/versions"), request.toString(), RestRequest.Method.POST)
    JSONArray.fromObject(res.getContent).get(0).asInstanceOf[JSONObject].asScala.map { kv =>
      val version = kv._1.asInstanceOf[String].split("/").last
      version
    }.toSeq
  }

  def addCommand(format: String, groupid: String, artifactId: String, version: String) = {
    val url = s"http://central.maven.org/maven2/${groupid.replaceAll("\\.", "/")}/${artifactId}/${version}"
    // fileName format e.g es, mongodb
    val finalUrl = s"${getOrDefaultUrl}/jar/manager/http?fileName=${URLEncoder.encode(artifactId, "utf-8")}&url=${URLEncoder.encode(url, "utf-8")}"
    val inputStream = Request.Get(finalUrl).execute().returnContent().asStream()
    val tmpLocation = new File("./dataousrce_upjars")
    if (!tmpLocation.exists()) {
      tmpLocation.mkdirs()
    }
    val jarFile = new File(tmpLocation.getPath + s"/${artifactId}-${version}.jar")
    if (!jarFile.exists()) {
      Files.copy(inputStream, jarFile.toPath)
    } else {
      inputStream.close()
    }
    jarFile.getPath
  }

  def loadJarInDriver(path: String) = {

    val systemClassLoader = ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader]
    val method = classOf[URLClassLoader].getDeclaredMethod("addURL", classOf[URL])
    method.setAccessible(true)
    method.invoke(systemClassLoader, new File(path).toURI.toURL)
  }

}

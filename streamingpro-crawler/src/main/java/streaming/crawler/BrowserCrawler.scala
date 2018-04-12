package streaming.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.{By, JavascriptExecutor, Proxy, WebDriver}
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

/**
  * Created by allwefantasy on 2/4/2018.
  */
object BrowserCrawler {
  def getPhantomJs(useProxy: Boolean, _ptPath: String): WebDriver = {
    ///usr/local/Cellar/phantomjs/2.1.1
    val ptPath = "phantomjs.binary.path"
    if (_ptPath != null) {
      System.setProperty(ptPath, _ptPath)
    }

    //    def isMac() = {
    //      val OS = System.getProperty("os.name").toLowerCase()
    //      OS.indexOf("mac") >= 0 && OS.indexOf("os") > 0 && OS.indexOf("x") > 0
    //    }
    //
    //    if (isMac()) {
    //      System.setProperty(ptPath, "/usr/local/Cellar/phantomjs/2.1.1/bin/phantomjs")
    //    }

    if (!System.getProperties.containsKey(ptPath) && _ptPath == null) {
      throw new RuntimeException("phantomjs.binary.path is not set")
    }

    val desiredCapabilities = DesiredCapabilities.phantomjs()
    desiredCapabilities.setCapability("phantomjs.page.settings.userAgent", "Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:50.0) Gecko/20100101 Firefox/50.0")
    desiredCapabilities.setCapability("phantomjs.page.customHeaders.User-Agent", "Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:50.0) Gecko/20100101 　　Firefox/50.0")
    if (useProxy) {
      val proxy = new Proxy()
      proxy.setProxyType(org.openqa.selenium.Proxy.ProxyType.MANUAL)
      proxy.setAutodetect(false)
      var proxyStr = ""
      do {
        proxyStr = ProxyUtil.getProxy()
      } while (proxyStr.length == 0)

      proxy.setHttpProxy(proxyStr)
      desiredCapabilities.setCapability(CapabilityType.PROXY, proxy)
    }

    new PhantomJSDriver(desiredCapabilities)


  }

  def request(url: String, ptPath: String, c_flag: String = "", pageNum: Int = 0, pageScrollTime: Int = 1000, timeout: Int = 10, useProxy: Boolean = false): Document = {
    var webDriver: WebDriver = null
    try {
      webDriver = getPhantomJs(useProxy, ptPath)
      webDriver.get(url)
      val wait = new WebDriverWait(webDriver, timeout)
      if (!c_flag.isEmpty) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id(c_flag)))
      }
      //---------------
      if (pageNum > 0) {
        val jse = webDriver.asInstanceOf[JavascriptExecutor]
        (0 until pageNum).foreach { f =>
          jse.executeScript("window.scrollBy(0,document.body.scrollHeight+50)", "")
          Thread.sleep(pageScrollTime)
        }

      }
      //---------------

      val document = Jsoup.parse(webDriver.getPageSource())
      document
    } finally {
      if (webDriver != null) {
        webDriver.quit()
      }
    }
  }

  def main(args: Array[String]): Unit = {
    //println(request("https://wwww.baidu.com", "su").body())
  }
}

package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import com.softwaremill.sttp._

object scalarr {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("scalarr.conf")
    val sonarrBase = "http://localhost:8989/api"
    val keySonarr = config.getString("sonarr.apikey")

    implicit val backend = HttpURLConnectionBackend()
    
    val firstRequest = sttp
      .get(uri"$sonarrBase/diskspace")
      .header("X-Api-Key", keySonarr)

    val firstResponse = firstRequest.send()
    val parsed = ujson.read(firstResponse.unsafeBody)
    println(parsed.render(indent = 2))
    println(parsed(0)("label"))
  }
}

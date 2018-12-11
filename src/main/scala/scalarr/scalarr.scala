package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import com.softwaremill.sttp._

object scalarr {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("scalarr.conf")
    val sonarrBase = "http://localhost:8989/api"
    val keySonarr = config.getString("sonarr.apikey")

    val firstRequest = sttp
      .get(uri"$sonarrBase/diskspace")
      .header("X-Api-Key", keySonarr)

    implicit val backend = HttpURLConnectionBackend()
    val firstResponse = firstRequest.send()
    
    // firstResponse: Response[String]
    println(firstResponse.body)
  }
}

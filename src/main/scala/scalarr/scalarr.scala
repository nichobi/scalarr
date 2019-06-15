package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import com.softwaremill.sttp._
import org.jline._

object scalarr {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("scalarr.conf")
    val sonarrBase = config.getString("sonarr.adress") + "/api"
    val keySonarr = config.getString("sonarr.apikey")

    implicit val backend = HttpURLConnectionBackend()
    
    val request = sttp
      .get(uri"$sonarrBase/diskspace")
      .header("X-Api-Key", keySonarr)

    val response = request.send()
    val parsed = ujson.read(response.unsafeBody)
    println(s"label = ${parsed(0)("label")}")
  }

  def interactive = {
    var keepGoing = true
    while(keepGoing) {
      scala.io.StdIn.readLine() match {
        case "test" => println("hi")
        case "exit" => keepGoing = false; println("goodbye")
        case _ => println("i see")
      }
    }
  }
}

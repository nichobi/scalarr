package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import com.softwaremill.sttp._
import org.jline

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
    val reader = jline.reader.LineReaderBuilder.builder.build()
    while(keepGoing) {
       reader.readLine.split(" ").toList match {
        case "hello" :: tail => println("hi")
        case "add" :: tail => 
        case "exit" :: tail => keepGoing = false; println("goodbye")
        case _ => println("Unkown command")
      }
    }
  }
}

package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import org.jline

object scalarr {
  val config = ConfigFactory.load("scalarr.conf")
  val sonarrAddress = config.getString("sonarr.address")
  val sonarrPort = config.getInt("sonarr.port")
  val sonarrKey = config.getString("sonarr.apikey")
  val sonarr = Sonarr(sonarrAddress, sonarrPort, sonarrKey)

  def main(args: Array[String] = Array.empty[String]): Unit = {
    interactive

//    val request = sttp
//      .get(uri"$sonarrBase/diskspace")
//      .header("X-Api-Key", keySonarr)
//
//    val response = request.send()
//    val parsed = ujson.read(response.unsafeBody)
//    println(s"label = ${parsed(0)("label")}")

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

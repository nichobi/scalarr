package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import org.jline
import scala.util.{Try, Success, Failure}

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
    implicit val reader = jline.reader.LineReaderBuilder.builder.build()
    while(keepGoing) {
       reader.readLine.split(" ").toList match {
        case "hello" :: tail => println("hi")
        case "search" :: tail => search(tail.mkString(" "))
        case "exit" :: tail => keepGoing = false; println("goodbye")
        case _ => println("Unkown command")
      }
    }
  }
  
  def search(term: String)(implicit reader: org.jline.reader.LineReader) = {
    val results = sonarr.search(term)
    if(results.isEmpty) println("no results")
    else {
      results.zipWithIndex.foreach{case (s, i) => println(s"($i) $s")}
      Try(reader.readLine("Add: ").toInt) match {
        case Success(value) if(results.indices.contains(value)) =>
          add(results(value))
        case _ => println("No series added")
      }
    }
  }

  def add(series: Series) = ???
}

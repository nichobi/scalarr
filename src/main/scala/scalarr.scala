package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import org.jline
import org.jline.reader.impl.completer.StringsCompleter
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._

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
    val completionStrings = Seq("hello", "lookup", "exit")
    val completer = new StringsCompleter(completionStrings.asJava)
    implicit val reader = jline.reader.LineReaderBuilder.builder
      .completer(completer).build()
    println("Welcome to Scalarr")
    while(keepGoing) {
       reader.readLine("Command: ").split(" ").toList match {
        case "hello" :: tail => println("hi")
        case "lookup" :: tail => lookup(tail.mkString(" "))
        case "exit" :: tail => keepGoing = false; println("Exiting...")
        case _ => println("Unkown command")
      }
    }
  }
  
  def lookup(term: String)(implicit reader: org.jline.reader.LineReader) = {
    val results = sonarr.lookup(term)
    if(results.isEmpty) println("no results")
    else {
      results.zipWithIndex.foreach{case (s, i) => println(lookupFormat(s, i))}
      Try(reader.readLine("Add series (index): ").toInt) match {
        case Success(value) if(results.indices.contains(value)) =>
          add(results(value))
        case _ => println("Invalid selection")
      }
    }

    def lookupFormat(s: Series, i: Int): String = s"""($i) ${s.title} - ${s.year}
   |    ${s.status} - Seasons: ${s.seasonCount}""".stripMargin
  }

  def add(series: Series) = ???
}

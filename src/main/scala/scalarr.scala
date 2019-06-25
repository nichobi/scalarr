package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import org.jline
import org.jline.reader.impl.completer.StringsCompleter
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._
import org.jline.reader.LineReader
import scala.util.control.Exception.allCatch

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
    val completionStrings = Seq("hello", "lookup", "exit", "series")
    val completer = new StringsCompleter(completionStrings.asJava)
    implicit val reader = jline.reader.LineReaderBuilder.builder
      .completer(completer).build()
    println(s"Connected to Sonarr ${sonarr.version} at $sonarrAddress:$sonarrPort")
    while(keepGoing) {
       reader.readLine("Command: ").split(" ").toList match {
        case "hello" :: tail => println("hi")
        case "lookup" :: tail => lookup(tail.mkString(" "))
        case "series" :: tail => series(tail.mkString(" "))
        case "exit" :: tail => keepGoing = false; println("Exiting...")
        case _ => println("Unkown command")
      }
    }
  }

  def lookup(term: String)(implicit reader: LineReader) = {
    val results = sonarr.lookup(term)
    if(results.isEmpty) println("no results")
    else {
      chooseFrom(results, "Add series: ", lookupFormat) match {
        case Some(series) =>
          println(s"Adding series: ${series.title}");
          add(series)
        case _ => println("Invalid selection")
      }
    }

    def lookupFormat(s: Series): String = s"""${s.title} - ${s.year}
     |    ${s.status} - Seasons: ${s.seasonCount}""".stripMargin

  }

  def series(query: String)(implicit reader: LineReader) = {
    val result = sonarr.seriesSearch(query)
    chooseFrom(result, "Series: ") match {
      case Some(series) =>
        chooseFrom(sonarr.getEpisodes(series.id.get), "Season: ")
      case _ => println("No matching series")
    }
  }

  def add(series: Series) = ???

  def makeString[A](a: A) = a.toString
  def chooseFrom[A] (options: Seq[A], prompt: String)
                    (implicit reader: LineReader): Option[A] = {
    chooseFrom(options, prompt, makeString)
  }

  def chooseFrom[A] (options: Seq[A], prompt: String, f: A => String) 
                    (implicit reader: LineReader): Option[A] = {
      options.zipWithIndex.foreach({case (o, i) => println(s"($i) ${f(o)}")})
      allCatch.opt(options(reader.readLine(prompt).toInt)) 
  }
}

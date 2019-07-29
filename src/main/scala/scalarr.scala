package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import org.jline
import org.jline.reader.impl.completer.StringsCompleter
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import org.jline.reader.LineReader
import scala.util.control.Exception.allCatch
import scala.collection.SortedMap

object scalarr {
  val scalarrLogo = """
 ____
/    '              |
\____   .--.  .--.  |  .--.  . __  . __
     \ /     /   \  | /   \  |/  ' |/  '
'____/ \.__, \___/\,| \___/\,|     |

""".drop(1).dropRight(2)
  val configFolder = os.home/".config"/"scalarr"
  val configFile = configFolder/"scalarr.conf"
  if(!os.exists(configFolder)) {
    os.makeDir.all(configFolder)
  }
  if(!os.exists(configFile)) {
    val defaultConfig = os.read(os.resource/"scalarr.conf")
    os.write(configFile, defaultConfig)
  }
  val config = ConfigFactory.parseFile(configFile.toIO)
  val sonarrAddress = config.getString("sonarr.address")
  val sonarrPort = config.getInt("sonarr.port")
  val sonarrKey = config.getString("sonarr.apikey")
  val sonarr = Sonarr(sonarrAddress, sonarrPort, sonarrKey)

  def main(args: Array[String] = Array.empty[String]): Unit = {
    println(scalarrLogo)
    println(s"Connected to Sonarr ${sonarr.version} at $sonarrAddress:$sonarrPort")
    interactive
  }

  def interactive = {
    var keepGoing = true
    val completionStrings = Seq("hello", "lookup", "exit", "series").sorted
    val completer = new StringsCompleter(completionStrings.asJava)
    implicit val reader = jline.reader.LineReaderBuilder.builder
      .completer(completer).build()
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
        println(series.toString)
        chooseFrom(sonarr.getEpisodes(series.id), "Season: ", makeString, seasonN) match {
          case Some(season) => chooseFrom(season.eps, "Episode: ", makeString, epN) match {
            case Some(episode) => println(episode.toString)
            case _ => println("No matching episode")
          }
          case _ => println("No matching episode")
        }
      case _ => println("No matching series")
    }
    def seasonN(s: Season) = s.n
    def epN(ep: Episode) = ep.episodeNumber
  }

  def add(series: Series) = ???

  def makeString[A](a: A) = a.toString
  def chooseFrom[A] (options: Seq[A], prompt: String)
                    (implicit reader: LineReader): Option[A] = {
    chooseFrom(options, prompt, makeString)
  }

  def chooseFrom[A] (options: Seq[A], prompt: String, f: A => String)
                    (implicit reader: LineReader): Option[A] = {
      if(options.size == 1) Some(options.head)
      else {
        options.zipWithIndex.foreach({case (o, i) => println(s"($i) ${f(o)}")})
        allCatch.opt(options(reader.readLine(prompt).toInt))
      }
  }

  def chooseFrom[A] (options: Seq[A], prompt: String, 
                     fString: A => String, indexer: A => Int)
                    (implicit reader: LineReader): Option[A] = {
      if(options.size == 1) Some(options.head)
      else {
        val map = SortedMap.empty[Int, A] ++ options.map(x => indexer(x) -> x)
        map.foreach{case (i, x) => println(s"($i) ${fString(x)}")}
        allCatch.opt(map(reader.readLine(prompt).toInt))
      }
  }
}

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
    val completionStrings = Seq("lookup", "exit", "series").sorted
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

  def lookup(term: String)(implicit reader: LineReader): Unit = {
    sonarr.lookup(term) match {
      case Success(results) => if(results.isEmpty) println("no results")
        else {
          chooseFrom(results, "series", lookupFormat) match {
            case Some(series) =>
              println(s"Adding series: ${series.title}");
              add(series)
            case _ => println("Invalid selection")
          }
        }
      case x => println("Search failed")
        println(x)
    }

    def lookupFormat(s: Series): String = s"""${s.title} - ${s.year}
     |    ${s.status} - Seasons: ${s.seasonCount}""".stripMargin

  }

  def series(query: String)(implicit reader: LineReader) = {
    val result = sonarr.seriesSearch(query)
    chooseFromTry(result, "series") match {
      case Some(series) =>
        println(series.toString)
        chooseFromTry(sonarr.getEpisodes(series.id), "season", makeString, seasonN) match {
          case Some(season) => chooseFrom(season.eps, "episode", makeString, epN) match {
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

  }

  def makeString[A]: A => String = _.toString

  def chooseFrom[A] (options: Seq[A], prompt: String, fString: A => String = makeString)
                    (implicit reader: LineReader): Option[A] = {
      if(options.size == 1) {
        val result = options.head
        println(s"${prompt.capitalize}: $result")
        Some(result)
      } else {
        options.zipWithIndex.foreach({case (o, i) => println(s"($i) ${fString(o)}")})
        allCatch.opt(options(reader.readLine(s"Choose a $prompt: ").toInt))
      }
  }

  def chooseFrom[A] (options: Seq[A], prompt: String, 
                     fString: A => String, indexer: A => Int)
                    (implicit reader: LineReader): Option[A] = {
      if(options.size == 1) {
        val result = options.head
        println(s"${prompt.capitalize}: $result")
        Some(result)
      } else {
        val map = SortedMap.empty[Int, A] ++ options.map(x => indexer(x) -> x)
        map.foreach{case (i, x) => println(s"($i) ${fString(x)}")}
        allCatch.opt(map(reader.readLine(s"Choose a $prompt: ").toInt))
      }
  }

  def chooseFromTry[A] (optionsTry: Try[Seq[A]], prompt: String, fString: A => String = makeString)
                    (implicit reader: LineReader): Option[A] = optionsTry match {
    case Success(options) => chooseFrom(options, prompt, fString)
    case x => println(s"Request failed:\n$x")
      None
  }
                      
  def chooseFromTry[A] (optionsTry: Try[Seq[A]], prompt: String, 
                        fString: A => String, indexer: A => Int) 
                       (implicit reader: LineReader): Option[A] = optionsTry match {
    case Success(options) => chooseFrom(options, prompt, fString, indexer)
    case x => println(s"Request failed:\n$x")
      None
  }
}

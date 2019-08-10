package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import org.jline
import org.jline.reader.impl.completer.StringsCompleter
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import org.jline.reader.LineReader
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
  if(!os.exists(configFolder)) os.makeDir.all(configFolder)
  val configFile = configFolder/"scalarr.conf"
  if(!os.exists(configFile)) {
    val defaultConfig = os.read(os.resource/"scalarr.conf")
    os.write(configFile, defaultConfig)
  }
  val config: Config = ConfigFactory.parseFile(configFile.toIO)
  val sonarrAddress = config.getString("sonarr.address")
  val sonarrPort = config.getInt("sonarr.port")
  val sonarrKey = config.getString("sonarr.apikey")
  val sonarr = Sonarr(sonarrAddress, sonarrPort, sonarrKey)

  def main(args: Array[String] = Array.empty[String]): Unit = {
    println(scalarrLogo)
    sonarr.version match {
      case Success(version) => 
        println(s"Connected to Sonarr $version at $sonarrAddress:$sonarrPort")
        interactive
      case Failure(error) => println(s"Failed to connect to Sonarr at $sonarrAddress:$sonarrPort")
        println(error)
    }
  }

  def interactive() = {
    var keepGoing = true
    val completionStrings = Seq("lookup", "exit", "series").sorted
    val completer = new StringsCompleter(completionStrings.asJava)
    implicit val reader = jline.reader.LineReaderBuilder.builder
      .completer(completer).build()
    while(keepGoing) {
       reader.readLine("Command: ").split(" ").toList match {
        case "hello" :: _ => println("hi")
        case "lookup" :: tail => lookup(tail.mkString(" "))
        case "series" :: tail => series(tail.mkString(" "))
        case "exit" :: _ => keepGoing = false; println("Exiting...")
        case _ => println("Unkown command")
      }
    }
  }

  def lookup(term: String)(implicit reader: LineReader): Unit = {
    for {
      results <- sonarr.lookup(term)
      series <- chooseFrom(results, "series", lookupFormat)
    } add(series)

    def lookupFormat(s: Series): String = s"""${s.title} - ${s.year}
     |    ${s.status} - Seasons: ${s.seasonCount}""".stripMargin

  }

  def series(query: String)(implicit reader: LineReader): Unit = {
    for {
      results <- sonarr.seriesSearch(query)
      series <- chooseFrom(results, "series")
      seasons <- sonarr.getEpisodes(series.id)
      season <- chooseFrom(seasons, "season", makeString, seasonN)
      episode <- chooseFrom(season.eps, "episode", makeString, epN)
    } println(episode)
    def seasonN(s: Season): Int = s.n
    def epN(ep: Episode): Int = ep.episodeNumber
  }

  def add(series: Series)(implicit reader: LineReader): Unit = {
    for {
      rootFolders <- sonarr.rootFolders
      rootFolder <- chooseFrom(rootFolders, "root folder")
      qualityProfiles <- sonarr.profiles
      qualityProfile <- chooseFrom(qualityProfiles, "quality profile")
      result <- Try(sonarr.add(series, rootFolder, qualityProfile))
    } result match {
      case Success(_) => println(s"Added $series")
      case Failure(err) => println(s"Error: ${err.getMessage}")
    }
  }

  def makeString[A]: A => String = _.toString

  def chooseFrom[A] (options: Seq[A], prompt: String, fString: A => String = makeString)
                    (implicit reader: LineReader): Try[A] = {
      val result = Try(options.size match {
        case 0 => throw new java.util.NoSuchElementException("No options to pick from")
        case 1 => options.head
        case _ => options.zipWithIndex.foreach({case (o, i) => println(s"($i) ${fString(o)}")})
          options(reader.readLine(s"Choose a $prompt: ").toInt)
      })
      result match {
        case Success(option) => println(s"${prompt.capitalize}: $option")
        case Failure(err) => println(s"Failed to pick $prompt: $err")
      }
      result
  }

  def chooseFrom[A] (options: Seq[A], prompt: String,
                     fString: A => String, indexer: A => Int)
                    (implicit reader: LineReader): Try[A] = Try {
      val result = options.size match {
        case 0 => throw new java.util.NoSuchElementException("No options to pick from")
        case 1 => options.head
        case _ => val map = SortedMap.empty[Int, A] ++ options.map(x => indexer(x) -> x)
          map.foreach{case (i, x) => println(s"($i) ${fString(x)}")}
          map(reader.readLine(s"Choose a $prompt: ").toInt)
      }
      result match {
        case Success(option) => println(s"${prompt.capitalize}: $option")
        case Failure(err) => println(s"Failed to pick option: $err")
      }
      result
  }

}

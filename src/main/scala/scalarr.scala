package scalarr
import com.typesafe.config.{Config,ConfigFactory}
import scala.util.{Success, Failure}
import util.{mergeLines, Reader}
import util.interactive._
import cats.Show
import cats.implicits._

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
  implicit val sonarr = Sonarr(sonarrAddress, sonarrPort, sonarrKey)

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
    implicit val reader = Reader()
    while(keepGoing) {
      reader.commandReader.readLine("Command: ").split(" ").toList match {
        case "hello"  :: _    => println("hi")
        case "add"    :: tail => lookup(tail.mkString(" "))
        case "series" :: tail => series(tail.mkString(" "))
        case "import" :: _    => importFiles
        case "exit"   :: _    => keepGoing = false; println("Exiting...")
        case default  :: _    => println(s"Unkown command: $default")
        case _ =>
      }
    }
  }

  def lookup(term: String)(implicit reader: Reader): Unit = {
    implicit val showSeries: Show[Series] = Show.show{s => 
      mergeLines(sonarr.posterOrBlank(s), s"""${s.title} - ${s.year}
      |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin)
    }

    for {
      results <- sonarr.lookup(term)
      series  <- chooseFrom(results, "series")(reader, showSeries)
    } add(series)
  }

  def series(query: String)(implicit reader: Reader): Unit = {
    for {
      results <- sonarr.seriesSearch(query)
      series  <- chooseFrom(results, "series")
      seasons <- sonarr.seasons(series)
      season  <- chooseFrom(seasons, "season", seasonN)
      episode <- chooseFrom(season.eps, "episode", epN)
    } println(episode)
    def seasonN(s: Season): Int = s.n
    def epN(ep: Episode): Int = ep.episodeNumber

    implicit val showSeries: Show[Series] = Show.show{s =>
      mergeLines(sonarr.posterOrBlank(s), s"""${s.title} - ${s.year}
      |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin)
    }
  }

  def add(series: Series)(implicit reader: Reader): Unit = {
    for {
      rootFolders     <- sonarr.rootFolders
      rootFolder      <- chooseFrom(rootFolders, "root folder")
      qualityProfiles <- sonarr.profiles
      qualityProfile  <- chooseFrom(qualityProfiles, "quality profile")
    } sonarr.add(series, rootFolder, qualityProfile) match {
      case Success(_) => println(s"Added $series")
      case Failure(err) => println(s"Error: ${err.getMessage}")
    }
  }

  def importFiles(implicit reader: Reader): Unit = {
    val showCopyBoolean: Show[Boolean] = Show.show(if(_) "Copy" else "Move")
    for {
      path <- reader.readPath
      copy <- chooseFrom(Seq(true, false), "import mode")(reader, showCopyBoolean)
    } sonarr.importPath(path, copy)
  }
}


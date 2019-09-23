package scalarr
import com.typesafe.config.{Config, ConfigFactory}
import scala.util.Try
import util.{putStrLn, putStr, mergeLines, Reader}
import util.interactive._
import cats.Show
import cats.implicits._
import zio._

object scalarr extends App {
  val scalarrLogo = """
 ____
/    '              |
\____   .--.  .--.  |  .--.  . __  . __
     \ /     /   \  | /   \  |/  ' |/  '
'____/ \.__, \___/\,| \___/\,|     |

""".drop(1).dropRight(1)
  def getConfigPath: Task[os.Path] = Task {
    val configFolder = Try { os.Path(sys.env("XDG_CONFIG_HOME")) }
      .getOrElse(os.home / ".config") / "scalarr"
    configFolder / "scalarr.conf"
  }

  def createConfigFile(configFile: os.Path): Task[Unit] = Task {
    val configFolder = configFile / os.up
    if (!os.exists(configFolder)) os.makeDir.all(configFolder)

    if (!os.exists(configFile)) {
      val defaultConfig = os.read(os.resource / "scalarr.conf")
      os.write(configFile, defaultConfig)
    }
  }

  def readConfig(configFile: os.Path): Task[Config] = Task(ConfigFactory.parseFile(configFile.toIO))

  def createSonarr(config: Config): Task[Sonarr] =
    for {
      address <- Task(config.getString("sonarr.address"))
      port    <- Task(config.getInt("sonarr.port"))
      key     <- Task(config.getString("sonarr.apikey"))
      sonarr  <- Task(Sonarr(address, port, key))
    } yield sonarr

  def run(args: List[String]): IO[Nothing, Int] = scalarr.fold(_ => 1, _ => 0)

  def scalarr: Task[Unit] = {
    for {
      _          <- putStrLn(scalarrLogo)
      configPath <- getConfigPath
      _          <- createConfigFile(configPath)
      config     <- readConfig(configPath)
      sonarr     <- createSonarr(config)
      version    <- sonarr.version
      address    <- Task.succeed(sonarr.address)
      port       <- Task.succeed(sonarr.port)
      _          <- putStrLn(s"Connected to Sonarr $version at $address:$port")
      reader     <- Task.succeed(Reader())
      _          <- interactive(sonarr, reader)
    } yield ()
  }

  def interactive(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    val action = for {
      command <- reader.readCommand("Command: ")
      repeat <- command.split(" ").toList match {
        case "hello"  :: _    => putStrLn("hi").as(true)
        case "add"    :: tail => lookup(tail.mkString(" ")).as(true)
        case "series" :: tail => series(tail.mkString(" ")).as(true)
        case "import" :: _    => importFiles.as(true)
        case "exit"   :: _    => putStrLn("Exiting...").as(false)
        case default  :: _    => putStrLn(s"Unkown command: $default").as(true)
        case _ => Task.succeed(true)
      }
      _ <- if (repeat) interactive else Task.unit
    } yield ()
    action.orElse(interactive)
  }

  def lookup(term: String)(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    implicit val showSeries: Show[Series] = Show.show { s =>
      mergeLines(sonarr.posterOrBlank(s), s"""${s.title} - ${s.year}
      |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin)
    }

    for {
      results <- sonarr.lookup(term)
      series  <- chooseFrom(results, "series")(reader, showSeries)
      _       <- add(series)
    } yield ()
  }

  def series(query: String)(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    implicit val showSeries: Show[AddedSeries] = Show.show { s =>
      mergeLines(sonarr.posterOrBlank(s), s"""${s.title} - ${s.year}
      |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin)
    }
    def seasonN(s: Season): Int = s.n
    def epN(ep: Episode): Int = ep.episodeNumber

    for {
      results <- sonarr.seriesSearch(query)
      series  <- chooseFrom(results, "series")
      seasons <- sonarr.seasons(series)
      season  <- chooseFrom(seasons, "season", seasonN)
      episode <- chooseFrom(season.eps, "episode", epN)
      _       <- putStrLn(episode.toString)
    } yield ()
  }

  def add(series: Series)(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    for {
      rootFolders     <- sonarr.rootFolders
      rootFolder      <- chooseFrom(rootFolders, "root folder")
      qualityProfiles <- sonarr.profiles
      qualityProfile  <- chooseFrom(qualityProfiles, "quality profile")
      _               <- sonarr.add(series, rootFolder, qualityProfile)
      _               <- putStrLn(s"Added $series")
    } yield ()
  }

  def importFiles(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    val showCopyBoolean: Show[Boolean] = Show.show(if (_) "Copy" else "Move")
    for {
      path <- reader.readPath("Path: ")
      copy <- chooseFrom(Seq(true, false), "import mode")(reader, showCopyBoolean)
    } yield sonarr.importPath(path, copy)
  }
}

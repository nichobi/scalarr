package scalarr
import com.typesafe.config.{Config, ConfigFactory}
import scalarr.util.formatting.mergeLines
import scalarr.util.console.{putStrLn, Reader}
import scalarr.util.interactive._
import scalarr.util.art.generateLogo
import scalarr.sonarr._
import cats.Show
import cats.implicits._
import zio.{App, IO, Task}

object main extends App {
  def getConfigPath: Task[os.Path] =
    Task(sys.env("XDG_CONFIG_HOME"))
      .map(os.Path(_))
      .orElse(Task(os.home / ".config"))
      .map(_ / "scalarr" / "scalarr.conf")

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

  def run(args: List[String]): IO[Nothing, Int] = main.fold(_ => 1, _ => 0)

  def main: Task[Unit] =
    for {
      _          <- putStrLn(generateLogo)
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

  def interactive(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    val action = for {
      command <- reader.readCommand("Command: ")
      repeat <- command.split(" ").toList match {
                 case "hello" :: _     => putStrLn("hi").as(true)
                 case "add" :: tail    => lookup(tail.mkString(" ")).as(true)
                 case "series" :: tail => series(tail.mkString(" ")).as(true)
                 case "import" :: _    => importFiles.as(true)
                 case "exit" :: _      => putStrLn("Exiting...").as(false)
                 case default :: _     => putStrLn(s"Unkown command: $default").as(true)
                 case _                => Task.succeed(true)
               }
      _ <- if (repeat) interactive else Task.unit
    } yield ()
    action.catchAll(err => putStrLn(s"Error: $err") *> interactive)
  }

  def lookup(term: String)(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    def showSeries(posters: Map[Series, String]): Show[Series] = Show.show { s =>
      mergeLines(
        posters(s),
        s"""${s.title} - ${s.year}
           |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin
      )
    }

    for {
      results <- sonarr.lookup(term)
      posters <- Task.foreachPar(results)(series => Task(series -> sonarr.posterOrBlank(series)))
      series  <- chooseFrom(results, "series")(reader, showSeries(posters.toMap))
      _       <- add(series)
    } yield ()
  }

  def series(query: String)(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    def showSeries(posters: Map[Series, String]): Show[AddedSeries] = Show.show { s =>
      mergeLines(
        posters(s),
        s"""${s.title} - ${s.year}
           |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin
      )
    }
    def seasonN(s: Season): Int = s.n
    def epN(ep: Episode): Int   = ep.episodeNumber

    for {
      results <- sonarr.seriesSearch(query)
      posters <- Task.foreachPar(results)(series => Task(series -> sonarr.posterOrBlank(series)))
      series  <- chooseFrom(results, "series")(reader, showSeries(posters.toMap))
      seasons <- sonarr.seasons(series)
      season  <- chooseFrom(seasons, "season", seasonN)
      episode <- chooseFrom(season.eps, "episode", epN)
      _       <- putStrLn(episode.toString)
    } yield ()
  }

  def add(series: Series)(implicit sonarr: Sonarr, reader: Reader): Task[Unit] =
    for {
      rootFolders     <- sonarr.rootFolders
      rootFolder      <- chooseFrom(rootFolders, "root folder")
      qualityProfiles <- sonarr.profiles
      qualityProfile  <- chooseFrom(qualityProfiles, "quality profile")
      _               <- sonarr.add(series, rootFolder, qualityProfile)
      _               <- putStrLn(s"Added $series")
    } yield ()

  def importFiles(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    val showCopyBoolean: Show[Boolean] = Show.show(if (_) "Copy" else "Move")
    for {
      path <- reader.readPath("Path: ")
      copy <- chooseFrom(Seq(true, false), "import mode")(reader, showCopyBoolean)
    } yield sonarr.importPath(path, copy)
  }
}

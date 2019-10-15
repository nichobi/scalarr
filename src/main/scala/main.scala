package scalarr
import com.typesafe.config.{Config, ConfigFactory}
import scalarr.util.console.{putStrLn, Reader}
import scalarr.util.interactive._
import scalarr.util.art.generateLogo
import scalarr.sonarr._
import cats.Show
import cats.implicits._
import zio.{App, IO, Task}
import scalarr.util.actions._

object main extends App {
  def getConfigPath: Task[os.Path] =
    Task(sys.env("XDG_CONFIG_HOME"))
      .map(os.Path(_))
      .orElse(Task(os.home / ".config"))
      .map(_ / "scalarr" / "scalarr.conf")

  def createConfigFile(configFile: os.Path): Task[Unit] =
    for {
      configFolder <- Task(configFile / os.up)
      _            <- if (!os.exists(configFolder)) Task(os.makeDir.all(configFolder)) else Task.unit
      _ <- if (!os.exists(configFile)) for {
            defaultConfig <- Task(os.read(os.resource / "scalarr.conf"))
            _             <- Task(os.write(configFile, defaultConfig))
            _ <- putStrLn(
                  s"Wrote new config file to ${configFile.toString}\nPlease edit it and restart Scalarr")
          } yield ()
          else Task.unit
    } yield ()

  def readConfig(configFile: os.Path): Task[Config] = Task(ConfigFactory.parseFile(configFile.toIO))

  def createSonarr(config: Config): Task[Sonarr] =
    for {
      address <- Task(config.getString("sonarr.address"))
      port    <- Task(config.getInt("sonarr.port"))
      key     <- Task(config.getString("sonarr.apikey"))
      sonarr  <- Task(Sonarr(address, port, key))
    } yield sonarr

  def run(args: List[String]): IO[Nothing, Int] =
    main.foldM(err => putStrLn(s"Error: ${err.getMessage}").as(1), _ => Task.succeed(0))

  def main: Task[Unit] =
    for {
      _          <- putStrLn(generateLogo)
      configPath <- getConfigPath
      _          <- createConfigFile(configPath)
      config     <- readConfig(configPath)
      sonarr     <- createSonarr(config)
      version    <- sonarr.version
      _          <- putStrLn(s"Connected to Sonarr $version at ${sonarr.address}:${sonarr.port}")
      reader     <- Task.succeed(Reader())
      _          <- interactive(sonarr, reader)
    } yield ()

  def interactive(implicit sonarr: Sonarr, reader: Reader): Task[Unit] = {
    val action = for {
      command <- reader.readCommand("Command: ")
      repeat <- command.split(" ").toList match {
                 case "hello" :: _  => putStrLn("hi").as(true)
                 case "search" :: _ => lookup.as(true)
                 case "series" :: _ => series.as(true)
                 case "import" :: _ => importFiles.as(true)
                 case "exit" :: _   => putStrLn("Exiting...").as(false)
                 case default :: _  => putStrLn(s"Unkown command: $default").as(true)
                 case _             => Task.succeed(true)
               }
      _ <- if (repeat) interactive else Task.unit
    } yield ()
    action.catchAll(err => putStrLn(s"Error: $err") *> interactive)
  }

  def lookup(implicit sonarr: Sonarr, reader: Reader): Task[Unit] =
    for {
      term    <- reader.readString("Query: ")
      results <- sonarr.lookup(term)
      _       <- chooseAction(results)
    } yield ()

  def series(implicit sonarr: Sonarr, reader: Reader): Task[Unit] =
    for {
      query   <- reader.readString("Query: ")
      results <- sonarr.seriesSearch(query)
      _       <- chooseAction(results)
    } yield ()

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

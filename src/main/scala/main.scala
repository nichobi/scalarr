package scalarr
import com.typesafe.config.{Config, ConfigFactory}
import scalarr.util.console.{putStrLn, Reader}
import scalarr.util.interactive._
import scalarr.util.art.generateLogo
import scalarr.sonarr._
import cats.Show
import cats.implicits._
import zio.{App, IO, RIO, Task, ZIO}
import scalarr.util.actions._

object main extends App {
  case class ScalarrEnvironment(sonarr: Sonarr, reader: Reader, config: ScalarrConfig)
  case class ScalarrConfig(resultSize: Int)
  def getConfigPath: Task[os.Path] =
    Task(sys.env("XDG_CONFIG_HOME"))
      .map(os.Path(_))
      .orElse(Task(os.home / ".config"))
      .map(_ / "scalarr" / "scalarr.conf")

  def createConfigFile(configFile: os.Path): Task[Unit] =
    for {
      configFolder <- Task(configFile / os.up)
      _            <- Task.when(!os.exists(configFolder))(Task(os.makeDir.all(configFolder)))
      _ <- Task.when(!os.exists(configFile)) {
            for {
              defaultConfig <- Task(os.read(os.resource / "scalarr.conf"))
              _             <- Task(os.write(configFile, defaultConfig))
              _             <- putStrLn(s"""Wrote new config file to ${configFile.toString}
                               |Please edit it and restart Scalarr""".stripMargin)
            } yield ()
          }
    } yield ()

  def readConfig(configFile: os.Path): Task[Config] = Task(ConfigFactory.parseFile(configFile.toIO))

  def createSonarr(config: Config): Task[Sonarr] =
    for {
      address <- Task(config.getString("sonarr.address"))
      port    <- Task(config.getInt("sonarr.port"))
      key     <- Task(config.getString("sonarr.apikey"))
      sonarr  <- Task(Sonarr(address, port, key))
    } yield sonarr
  def createScalarrConfig(config: Config) =
    for {
      resultSize <- Task(config.getInt("scalarr.resultSize")).orElse(Task.succeed(5))
    } yield ScalarrConfig(resultSize)

  def run(args: List[String]): IO[Nothing, Int] =
    main.foldM(err => putStrLn(s"Error: ${err.getMessage}").as(1), _ => Task.succeed(0))

  def main: Task[Unit] =
    for {
      _             <- putStrLn(generateLogo)
      configPath    <- getConfigPath
      _             <- createConfigFile(configPath)
      config        <- readConfig(configPath)
      sonarr        <- createSonarr(config)
      scalarrConfig <- createScalarrConfig(config)
      version       <- sonarr.version
      _             <- putStrLn(s"Connected to Sonarr $version at ${sonarr.address}:${sonarr.port}")
      reader        <- Task.succeed(Reader())
      environment   <- Task.succeed(ScalarrEnvironment(sonarr, reader, scalarrConfig))
      _             <- interactive.provide(environment)
    } yield ()

  def interactive: RIO[ScalarrEnvironment, Unit] = {
    val action = for {
      reader  <- ZIO.access[ScalarrEnvironment](_.reader)
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

  def lookup: RIO[ScalarrEnvironment, Unit] =
    for {
      resultSize <- ZIO.access[ScalarrEnvironment](_.config.resultSize)
      term       <- ZIO.accessM[ScalarrEnvironment](_.reader.readString("Query: "))
      results    <- ZIO.accessM[ScalarrEnvironment](_.sonarr.lookup(term, resultSize))
      _          <- chooseAction(results)
    } yield ()

  def series: RIO[ScalarrEnvironment, Unit] =
    for {
      resultSize <- ZIO.access[ScalarrEnvironment](_.config.resultSize)
      query      <- ZIO.accessM[ScalarrEnvironment](_.reader.readString("Query: "))
      results    <- ZIO.accessM[ScalarrEnvironment](_.sonarr.seriesSearch(query, resultSize))
      _          <- chooseAction(results)
    } yield ()

  def add(series: Series): RIO[ScalarrEnvironment, Unit] =
    for {
      sonarr          <- ZIO.access[ScalarrEnvironment](_.sonarr)
      rootFolders     <- sonarr.rootFolders
      rootFolder      <- chooseFrom(rootFolders, "root folder")
      qualityProfiles <- sonarr.profiles
      qualityProfile  <- chooseFrom(qualityProfiles, "quality profile")
      _               <- sonarr.add(series, rootFolder, qualityProfile)
      _               <- putStrLn(s"Added $series")
    } yield ()

  def importFiles: RIO[ScalarrEnvironment, Unit] = {
    val showCopyBoolean: Show[Boolean] = Show.show(if (_) "Copy" else "Move")
    for {
      sonarr <- ZIO.access[ScalarrEnvironment](_.sonarr)
      path   <- ZIO.accessM[ScalarrEnvironment](_.reader.readPath("Path: "))
      copy   <- chooseFrom(Seq(true, false), "import mode")(showCopyBoolean)
    } yield sonarr.importPath(path, copy)
  }
}

name := "scalarr"

version := "1.0.2"

scalaVersion := "0.19.0-RC1"

libraryDependencies ++= Seq(
  "com.typesafe"          % "config"         % "1.4.0",
  "org.jline"             % "jline"          % "3.12.1",
  "org.fusesource.jansi"  % "jansi"          % "1.18",
  "dev.zio"               %% "zio"           % "1.0.0-RC15",
)

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core"          % "1.7.2",
  "com.lihaoyi"           %% "os-lib"        % "0.3.0",
  "com.lihaoyi"           %% "fansi"         % "0.2.7",
  "org.json4s"            %% "json4s-native" % "3.6.7",
).map(_.withDottyCompat(scalaVersion.value))

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-Ywarn-unused",
  "-feature",
  "-Xlint"
)
scalacOptions in (Compile, console) ++= Seq("-Ywarn-unused:-imports")

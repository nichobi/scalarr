name := "scalarr"

version := "1.0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core"          % "1.7.2",
  "com.typesafe"          % "config"         % "1.4.0",
  "org.jline"             % "jline"          % "3.12.1",
  "com.lihaoyi"           %% "os-lib"        % "0.3.0",
  "com.lihaoyi"           %% "fansi"         % "0.2.7",
  "org.fusesource.jansi"  % "jansi"          % "1.18",
  "dev.zio"               %% "zio"           % "1.0.0-RC15",
  "org.typelevel"         %% "cats-core"     % "2.0.0",
  "org.json4s"            %% "json4s-native" % "3.6.7"
)

scalacOptions := Seq("-unchecked",
                     "-deprecation",
                     "-Ywarn-dead-code",
                     "-Ywarn-unused",
                     "-feature",
                     "-Xlint")
scalacOptions in (Compile, console) ++= Seq("-Ywarn-unused:-imports")

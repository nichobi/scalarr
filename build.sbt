name := "scalarr"

version := "0.1.0"

scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "ujson" % "0.7.5",
  "com.softwaremill.sttp" %% "core" % "1.6.0",
  "com.typesafe" % "config" % "1.3.4",
  "org.jline" % "jline" % "3.11.0",
  "com.lihaoyi" %% "os-lib" % "0.3.0",
  "org.fusesource.jansi" % "jansi" % "1.18",
  "org.typelevel" %% "cats-core" % "2.0.0"
)

scalacOptions := Seq("-unchecked", "-deprecation", "Ywarn-dead-code", 
                     "-Ywarn-unused", "-feature", "-Xlint")

# scalarr
This will eventually become a command line tool for managing [Sonarr](https://github.com/Sonarr/Sonarr) &amp; [Radarr](https://github.com/Radarr/Radarr), fully written in Scala.

The project is still very early on in development, but currently looks like this:
![Preview gif](https://raw.githubusercontent.com/nichobi/scalarr/master/scalarr.gif)

# Getting started
## How to build this repo

This project is set up with [scala build tool, sbt](http://www.scala-sbt.org). To build Scalarr you just need to:

* Install sbt: http://www.scala-sbt.org/release/docs/Setup.html

* Clone or download this repo

* Open the repo directory `Scalarr`

* Run either of these commands:
  * `sbt run` to compile and run Scalarr on the fly
  * `sbt assembly` to build a jar of Scalarr and all its required libraries  
  This jar can then be run with `java -jar target/scala-2.12/scalarr-assembly-0.1.0-SNAPSHOT.jar`

sbt will automatically download and prepare all the required [dependencies](https://github.com/nichobi/scalarr/blob/master/build.sbt).

![Scalarr](https://raw.githubusercontent.com/nichobi/scalarr/master/logo.png)

Scalarr is a command line tool for managing [Sonarr](https://github.com/Sonarr/Sonarr), fully written in functional Scala.

The project is still very early on in development, but currently looks like this:
![Preview gif](https://raw.githubusercontent.com/nichobi/scalarr/master/scalarr.gif)

# Getting started
## How to build this repo

This project is set up with [scala build tool, sbt](http://www.scala-sbt.org). To build Scalarr you just need to:

* Install sbt: http://www.scala-sbt.org/release/docs/Setup.html

* Clone or download this repo

* Open the repo directory `Scalarr`

* Run either of these commands and sbt will download and prepare all [dependencies](https://github.com/nichobi/scalarr/blob/master/build.sbt):
  * `sbt run` to compile and run Scalarr on the fly
  * `sbt assembly` to build a jar of Scalarr and all its required libraries, in the directory `target/scala-2.13/`. The file will be called something like `scalarr-assembly-0.1.0-SNAPSHOT.jar`
sbt will automatically 
  
## Running scalarr
After you have your jar file, you can run it with java. For example if you have a file `scalarr.jar` in your current directory, run it with:
`java -jar scalarr.jar`
Or if you've just built it: 
`java -jar target/scala-2.13/scalarr-assembly-0.2.0-SNAPSHOT.jar`
  
On first launch a config file will be placed in your home directory's `.config` folder. Fill in your address, port and Sonarr API key and then run scalarr again to connect to your server. Once you're connected, you can cycle through available commands with the tab key.
  
Currently scalarr supports the following commands:  

  * `add` - Add a new series to your library  
  * `series` - View a series in your library and its seasons/episodes  
  * `import` - Automatically import any video files in the selected path
  * `exit` - Exit scalarr  

The wonderful logo and mascot were designed by [Harofax](https://github.com/harofax)


![Scalarr](https://raw.githubusercontent.com/nichobi/scalarr/master/logo.png)

Scalarr is a command line tool for managing [Sonarr](https://github.com/Sonarr/Sonarr), fully written in functional Scala.

Here's a preview of what it looks like
![Preview gif](https://raw.githubusercontent.com/nichobi/scalarr/master/scalarr.gif)

# Getting started
## Installation
Scalarr is available on the AUR. If you're on an Arch, you can install it from [here](https://aur.archlinux.org/packages/scalarr/).

For other systems head over to the [release page](https://github.com/nichobi/scalarr/releases), download a jar file, and follow the instructions further down to run it.
## How to build this repo

This project is set up with [scala build tool, sbt](http://www.scala-sbt.org). To build Scalarr you just need to:

* Install sbt: http://www.scala-sbt.org/release/docs/Setup.html

* Clone or download this repo

* Open the repo directory `Scalarr`

* Run either of these commands and sbt will download and prepare all [dependencies](https://github.com/nichobi/scalarr/blob/master/build.sbt):
  * `sbt run` to compile and run Scalarr on the fly
  * `sbt assembly` to build a jar of Scalarr and all its required libraries, in the directory `target/scala-2.13/`. The file will be called something like `scalarr-assembly-1.0.0.jar`
  
## Running scalarr
After you have your jar file, you can run it with java. For example if you have a file `scalarr.jar` in your current directory, run it with:
`java -jar scalarr.jar`
Or if you've just built it: 
`java -jar target/scala-2.13/scalarr-assembly-1.0.0.jar`
  
On first launch a config file will be placed in your home directory's `.config` folder. Fill in your address, port and Sonarr API key and then run scalarr again to connect to your server. Once you're connected, you can cycle through available commands with the tab key.
  
Currently scalarr supports the following commands:  

  * `search` - Perform a search for new series to add to your library  
  * `series` - View a series in your library and its seasons/episodes  
  * `import` - Automatically import any video files in the selected path  
  * `exit` - Exit scalarr  

# Acknowledgements
The wonderful logo and mascot were designed by [Harofax](https://github.com/harofax), saving Scalarr from my poor attempts at ASCII art.

Posters are drawn using a slightly modified version of [TerminalImageViewer](https://github.com/stefanhaustein/TerminalImageViewer).

Scalarr relies heavily on ZIO and I owe a lot to [John A. De Goes](https://github.com/jdegoes) not only for creating ZIO, but also for his excellent talks without which Scalarr would look nothing like it does today.

Much of the command flow was inspired by [beets](https://github.com/beetbox/beets).

Scalarr has several more [dependencies](https://github.com/nichobi/scalarr/blob/master/build.sbt), and I thank all their contributors for their work.

Finally, my thanks to everyone who has contributed to Scala itself, and [Björn Regnell](https://github.com/bjornregnell) for introducing me to and teaching the language at [LTH](https://www.lth.se/).


package scalarr.util
import scala.jdk.CollectionConverters._
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.builtins.Completers.DirectoriesCompleter

import zio._

object console {
  def putStrLn(line: String): UIO[Unit] =
    ZIO.effectTotal(println(line))
  def putStr(line: String): UIO[Unit] =
    ZIO.effectTotal(print(line))

  trait Reader {
    def readString(prompt: String): Task[String]
    def readCommand(prompt: String): Task[String]
    def readPath(prompt: String): Task[os.Path]
    def readOption(prompt: String, options: Seq[String]): Task[String]
  }
  class LiveReader extends Reader {
    lazy val stringReader          = LineReaderBuilder.builder.build()
    def readString(prompt: String) = Task(stringReader.readLine(prompt))

    private val commandStrings = Seq("search", "exit", "series", "import").sorted
    lazy val commandReader = LineReaderBuilder.builder
      .completer(new StringsCompleter(commandStrings.asJava))
      .build()
    def readCommand(prompt: String) = Task(commandReader.readLine(prompt))

    lazy val pathReader = LineReaderBuilder.builder
      .completer(new DirectoriesCompleter(os.pwd.toIO))
      .build
    def readPath(prompt: String) = Task(pathReader.readLine(prompt)).map(os.Path(_, os.pwd))

    def readOption(prompt: String, options: Seq[String]) = {
      val optionReader = LineReaderBuilder.builder
        .completer(new StringsCompleter(options.toSeq.sorted.asJava))
        .build()
      Task(optionReader.readLine(prompt).trim)
    }
  }
  object Reader {
    def apply() = new LiveReader
  }
}

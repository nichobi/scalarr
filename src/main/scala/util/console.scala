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

  class Reader() {
    private val commandStrings     = Seq("search", "exit", "series", "import").sorted
    lazy val commandReader = LineReaderBuilder.builder
      .completer(new StringsCompleter(commandStrings.asJava))
      .build()
    def readCommand(prompt: String) = Task(commandReader.readLine(prompt))

    lazy val pathReader = LineReaderBuilder.builder
      .completer(new DirectoriesCompleter(os.pwd.toIO))
      .build
    def readPath(prompt: String)   = Task(pathReader.readLine(prompt)).map(os.Path(_, os.pwd))
    private lazy val optionReader  = LineReaderBuilder.builder.build
    def readOption(prompt: String) = Task(optionReader.readLine(prompt))
    def readOption(prompt: String, options: Seq[String]) = {
      val optionCompletionReader = LineReaderBuilder.builder
        .completer(new StringsCompleter(options.toSeq.sorted.asJava))
        .build()
      Task(optionCompletionReader.readLine(prompt).trim)
    }
  }
  object Reader {
    def apply() = new Reader()
  }
}

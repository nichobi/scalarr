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
    private val commandStrings = Seq("add", "exit", "series", "import").sorted
    lazy val commandReader = LineReaderBuilder.builder
      .completer(new StringsCompleter(commandStrings.asJava))
      .build()
    def readCommand(prompt: String) = ZIO.effect(commandReader.readLine(prompt))

    lazy val pathReader = LineReaderBuilder.builder
      .completer(new DirectoriesCompleter(os.pwd.toIO))
      .build
    def readPath(prompt: String)   = ZIO.effect(pathReader.readLine(prompt)).map(os.Path(_, os.pwd))
    private lazy val optionReader  = LineReaderBuilder.builder.build
    def readOption(prompt: String) = ZIO.effect(optionReader.readLine(prompt))
  }
  object Reader {
    def apply() = new Reader()
  }
}

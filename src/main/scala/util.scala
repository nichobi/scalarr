package scalarr
import tiv.TerminalImageViewer
import com.softwaremill.sttp._
import scala.util.Try
import scala.collection.SortedMap
import org.fusesource.jansi.AnsiString
import scala.jdk.CollectionConverters._
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.builtins.Completers.DirectoriesCompleter
import cats.Show
import cats.implicits._
import zio._

object util {

  def putStrLn(line: String): UIO[Unit] =
    ZIO.effectTotal(println(line))
  def putStr(line: String): UIO[Unit] =
    ZIO.effectTotal(print(line))

  def mergeLines(strings: String*): String = {
    val columns =
      strings.map(_.split('\n').toSeq).map(lines => TextColumn(lines))
    val maxHeight = columns.map(_.height).max
    val lines = for (i <- 0 until maxHeight) yield {
      columns.foldLeft("")(_ + _.get(i) + " ")
    }
    lines.mkString("\n")
  }

  //The height and width values were arrived at by trial and error.
  //The relation between these numbers and the image size measured in characters is unclear.
  def imgConvert(url: Uri): Try[String] =
    Try(TerminalImageViewer.convert(url.toString, 27, 50))

  case class TextColumn(rawLines: Seq[String]) {
    val wrappedLines = rawLines.map(l => WrappedString(l))
    val height = wrappedLines.size
    val width = wrappedLines.map(_.size).max
    val lines = wrappedLines.map(l => l + (" " * (width - l.size)))

    def get(index: Int) =
      if (lines.indices.contains(index)) lines(index)
      else " " * width
  }

  case class WrappedString(input: String) {
    val jansi = new AnsiString(input + scala.Console.RESET)
    def size = jansi.getPlain.toString.size
    override def toString = jansi.toString + scala.Console.RESET
    def +(other: String): WrappedString = WrappedString(jansi.toString + other)
  }

  case class Reader() {
    private val commandStrings = Seq("add", "exit", "series", "import").sorted
    lazy val commandReader = LineReaderBuilder.builder
      .completer(new StringsCompleter(commandStrings.asJava))
      .build()
    def readCommand = ZIO.effect(commandReader.readLine)

    lazy val pathReader = LineReaderBuilder.builder
      .completer(new DirectoriesCompleter(os.pwd.toIO))
      .build
    def readPath = ZIO.effect(pathReader.readLine).map(os.Path(_, os.pwd))
    private lazy val optionReader = LineReaderBuilder.builder.build
    def readOption = ZIO.effect(optionReader.readLine)
  }

  object interactive {
    def makeString[A]: A => String = _.toString

    def chooseFrom[A](
        options: Seq[A],
        prompt: String
    )(implicit reader: Reader, showA: Show[A]): Task[A] = {
      val map = SortedMap((1 to options.size).zip(options): _*)
      chooseFromHelper(map, prompt)
    }

    def chooseFrom[A](options: Seq[A], prompt: String, indexer: A => Int)(
        implicit reader: Reader,
        showA: Show[A]
    ): Task[A] = {
      val map = SortedMap(options.map(o => indexer(o) -> o): _*)
      chooseFromHelper(map, prompt)
    }

    private def chooseFromHelper[A](
        options: SortedMap[Int, A],
        prompt: String
    )(implicit reader: Reader, showA: Show[A]): Task[A] = {

      implicit val showMap: Show[SortedMap[Int, A]] = Show.show { sm =>
        sm.map { case (i, x) => mergeLines(show"($i)", x.show) }.mkString("\n")
      }

      val result: Task[A] = options.size match {
        case 0 =>
          Task.fail(
            new java.util.NoSuchElementException("No options to pick from")
          )
        case 1 => Task.succeed(options.head._2)
        case _ =>
          for {
            _           <- putStrLn(options.show)
            _           <- putStr(s"Choose $prompt: ")
            indexString <- reader.readOption
            index       <- Task(indexString.toInt)
            value       <- Task(options(index))
          } yield value
      }
      result.foldM(
        err    => putStrLn(s"Failed to pick option: $err") *> Task.fail(err),
        chosen => putStrLn(s"${prompt.capitalize}: $chosen") *> Task.succeed(chosen)
      )
    }
  }
}

package scalarr.util
import org.fusesource.jansi.AnsiString

object formatting {
  def mergeLines(strings: String*): String = {
    val columns =
      strings.map(_.split('\n').toSeq).map(lines => TextColumn(lines))
    val maxHeight = columns.map(_.height).max
    val lines = for (i <- 0 until maxHeight) yield {
      columns.foldLeft("")(_ + _.get(i) + " ")
    }
    lines.mkString("\n")
  }

  case class TextColumn(rawLines: Seq[String]) {
    val wrappedLines = rawLines.map(l => WrappedString(l))
    val height       = wrappedLines.size
    val width        = wrappedLines.map(_.size).max
    val lines        = wrappedLines.map(l => l + (" " * (width - l.size)))

    def get(index: Int) =
      if (lines.indices.contains(index)) lines(index)
      else " " * width
  }

  case class WrappedString(input: String) {
    val jansi = new AnsiString(input + scala.Console.RESET)
    def size  = jansi.getPlain.toString.size

    override def toString               = jansi.toString + scala.Console.RESET
    def +(other: String): WrappedString = WrappedString(jansi.toString + other)
  }
}

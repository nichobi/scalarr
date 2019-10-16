package scalarr.util
import org.fusesource.jansi.AnsiString

object formatting {
  def mergeLines(strings: String*): String = {
    val columns   = strings.map(_.split('\n').toSeq).map(lines => TextColumn(lines))
    val maxHeight = columns.map(_.height).maxOption.getOrElse(0)
    val lines = for (i <- 0 until maxHeight) yield {
      columns.map(_.get(i)).mkString(" ")
    }
    lines.mkString("\n")
  }

  final private case class TextColumn(private val rawLines: Seq[String]) {
    private val wrappedLines = rawLines.map(l => WrappedString(l))
    val height               = wrappedLines.size
    val width                = wrappedLines.map(_.size).max
    private val paddedLines  = wrappedLines.map(l => l + (" " * (width - l.size))).toVector

    def get(index: Int) = paddedLines.lift(index).getOrElse(" " * width)
  }

  final private case class WrappedString(private val input: String) {
    private val jansi = new AnsiString(input + scala.Console.RESET)
    def size          = jansi.getPlain.toString.size

    override def toString = jansi.toString + scala.Console.RESET
    def +(other: String)  = WrappedString(jansi.toString + other)
  }

  def monitoredSymbol(monitored: Boolean) = if (monitored) "●" else "○"
}

package scalarr.util
import scala.util.Try
import tiv.TerminalImageViewer
import scalarr.util.formatting.mergeLines
import com.softwaremill.sttp._
import scala.util.Try

object art {

  //The height and width values were arrived at by trial and error.
  //The relation between these numbers and the image size measured in characters is unclear.
  def imgConvert(url: Uri): Try[String] =
    Try(TerminalImageViewer.convert(url.toString, 27, 50))

  def inverted(string: String): String = fansi.Color.Black(fansi.Back.White(string)).render
  def inverted(char: Char): String     = inverted(char.toString)
  val scalarrText =
    """            «     »
      | ╓══╖     «   \╪/   »
      | ║  ╙       «  ╫  »
      | ╙══╖ ╓══ ╓══╗ ╫ ╓══╗ ╓── ╓──
      | ╖  ║ ║   ║  ║ ╫ ║  ║ ║   ║
      | ╙══╜ ╙══ ╙══╚ ╙ ╙══╚ ╨   ╨""".stripMargin

  def generateLogo = mergeLines(scalarrText, randomMascot)

  def defaultMascot: String = generateMascot(mascotEyes.head)

  def randomMascot: String =
    generateMascot(mascotEyes(scala.util.Random.nextInt(mascotEyes.size)))
  def generateMascot(eyes: (Char, Char)): String = {
    val base =
      """     \╪/
        |┌─────┴─┬┐
        |│███████│╡┐
        |│█^██^██│╪╡
        |│∙►=-▬: │╡┘
        |╞╤══════╪╡""".stripMargin.replace("█", inverted(" "))
    val parts    = base.split("\\^")
    val combined = parts(0) + inverted(eyes._1) + parts(1) + inverted(eyes._2) + parts(2)
    combined
  }
  // format: off
    val mascotEyes: Seq[(Char, Char)] = Vector(
      ('^', '^'), ('☼', '☼'), ('≥', '≤'), ('*', '*'), ('$', '$'),
      ('=', '='), ('▲', '▲'), ('-', '-'), ('@', '@'), ('\'', '\''),
      ('T', 'T'), ('#', '#'), ('0', '0'), ('~', '~'), ('?', '?'),
      ('+', '+'), ('x', 'x'), ('\\', '/'), ('.', '.'), ('•', '•'),
      ('o', 'O'), ('u', 'u'), ('!', '!'), ('▬', '▬'), ('►', '►'),
      ('&', '&'), ('∆', '∆'), ('╭', '╮'), ('◕', '◕'), ('◉', '◉'),
      ('◤', '◤'), ('⁂', '⁂'), ('⁌', '⁍')
    )
    // format: on
}

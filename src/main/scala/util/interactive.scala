package scalarr.util
import scalarr.util.console._
import scalarr.util.formatting.mergeLines
import scala.collection.SortedMap
import cats.Show
import cats.implicits._
import zio.Task

object interactive {
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

    implicit val showFansi: Show[fansi.Str] = Show.show { _.render }
    implicit val showMap: Show[SortedMap[Int, A]] = Show.show { sm =>
      sm.map { case (i, x) => mergeLines(show"(${fansi.Color.LightBlue(i.toString)})", x.show) }
        .mkString("\n")
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
          indexString <- reader.readOption(s"Choose $prompt: ")
          index       <- Task(indexString.toInt)
          value       <- Task(options(index))
        } yield value
    }
    result.foldM(
      err => putStrLn(s"Failed to pick option: $err") *> Task.fail(err),
      chosen => putStrLn(s"${prompt.capitalize}: $chosen") *> Task.succeed(chosen)
    )
  }
}

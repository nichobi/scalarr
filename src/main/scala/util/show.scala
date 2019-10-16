package scalarr.util

import scala.language.implicitConversions

object show {
  class Show[A](private val f: A => String) {
    def show(a: A) = f(a)
  }
  object Show {
    def apply[A](f: A => String) = new Show(f)
    def fromToString[A <: Any]   = new Show[A](_.toString)
  }
  class Showable[A](a: A, showA: Show[A]) {
    def show = showA.show(a)
  }
  implicit def toShowable[A](a: A)(implicit showA: Show[A]) = new Showable(a, showA)

  implicit class ShowInterpolator(val sc: StringContext) extends AnyVal {
    def show(args: Showable[_]*): String = {
      val strings = sc.parts.iterator
      val showed  = args.map(_.show).iterator
      val sb      = new StringBuffer(strings.next)
      while (strings.hasNext) {
        sb.append(showed.next)
        sb.append(strings.next)
      }
      sb.toString
    }
  }
}

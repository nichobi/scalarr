package scalarr.util

import scalarr.main._
import scalarr.util.console._
import scalarr.util.formatting.mergeLines
import scalarr.sonarr._
import zio._
import cats.Show
import cats.implicits._

object actions {
  sealed trait Key {
    val key: String
  }
  final case class ActionKey(pre: String, key: String, post: String) extends Key {
    override def toString = pre + fansi.Color.LightBlue(key.toUpperCase) + post
  }
  final case class IndexKey(index: Int) extends Key {
    val key               = index.toString
    override def toString = "(" + fansi.Color.LightBlue(key.toUpperCase) + ")"
  }

  case class Action(key: Key, task: RIO[ScalarrEnvironment, Any], presentation: String = "") {
    override def toString =
      if (presentation != "") s"${key.toString} $presentation" else key.toString
  }
  object Action {
    implicit val showAction: Show[Action] =
      Show.show(action => action.toString)
  }

  // The KeyInterpolator takes a key of format "pre{key}post"
  // pre and post are optional, but key must be at least 1 character
  implicit class KeyInterpolator(val sc: StringContext) extends AnyVal {
    def key(args: Any*): ActionKey = {
      val str                     = sc.parts.mkString
      val pattern                 = "(.*)\\{(..*)\\}(.*)".r
      val pattern(pre, key, post) = str
      ActionKey(pre, key, post)
    }
  }

  private def actionsOf(a: Any): RIO[Sonarr, Seq[Action]] =
    for {
      sonarr <- ZIO.environment[Sonarr]
    } yield
      a match {
        case series: AddedSeries =>
          Seq(
            if (series.monitored) Action(key"un{m}onitor", sonarr.setMonitored(series, false))
            else Action(key"{m}onitor", sonarr.setMonitored(series, true))
          )
        case series: LookupSeries =>
          Seq(
            Action(key"{a}dd", add(series))
          )
        case season: Season =>
          Seq(
            Action(key"{s}earch", sonarr.search(season)),
            if (season.monitored) Action(key"un{m}onitor", sonarr.setMonitored(season, false))
            else Action(key"{m}onitor", sonarr.setMonitored(season, true))
          )
        case episode: Episode =>
          Seq(
            Action(key"{s}earch", sonarr.search(episode)),
            if (episode.monitored) Action(key"un{m}onitor", sonarr.setMonitored(episode, false))
            else Action(key"{m}onitor", sonarr.setMonitored(episode, true))
          )
        case _ => Seq.empty[Action]
      }
  private def childrenOf(a: Any): RIO[Sonarr, Seq[Action]] = {
    def showSeries(posters: Map[Series, String]): Show[Series] = Show.show { s =>
      mergeLines(
        posters(s),
        s"""${s.title} - ${s.year}
           |${s.status.capitalize} - Seasons: ${s.seasonCount}""".stripMargin
      )
    }
    a match {
      case series: AddedSeries =>
        for {
          sonarr <- ZIO.environment[Sonarr]
          actions <- sonarr
                      .seasons(series)
                      .map(_.map(season =>
                        Action(IndexKey(season.n), chooseAction(season), season.toString)))
        } yield actions
      case season: Season =>
        Task.succeed(
          season.eps
            .map(episode =>
              Action(IndexKey(episode.episodeNumber), chooseAction(episode), episode.toString))
        )
      case sequence: Seq[Series]
          if (!sequence.isEmpty && sequence.forall(_.isInstanceOf[Series])) =>
        for {
          sonarr <- ZIO.environment[Sonarr]
          posters <- Task.foreachPar(sequence)(series =>
                      Task(series -> sonarr.posterOrBlank(series)))
          actions <- Task.succeed(sequence.zipWithIndex.map {
                      case (series, index) =>
                        Action(IndexKey(index + 1),
                               chooseAction(series),
                               showSeries(posters.toMap).show(series))
                    })
        } yield actions
      case sequence: Seq[_] =>
        Task.succeed(sequence.zipWithIndex.map {
          case (value, index) => Action(IndexKey(index + 1), chooseAction(value))
        })
      case _ => Task.succeed(Seq.empty[Action])
    }
  }
  private def showActions(children: Seq[Action], actions: Seq[Action]): Task[Any] = {
    val showActions: Show[Seq[Action]] = Show.show { seq =>
      seq.map(_.show).mkString(", ")
    }
    val showChildren: Show[Seq[Action]] = Show.show { seq =>
      seq.map(action => mergeLines(action.key.toString, action.presentation)).mkString("\n")
    }
    for {
      _ <- putStrLn(showChildren.show(children))
      _ <- putStrLn(showActions.show(actions))
    } yield ()

  }
  private def pickAction(actions: Map[String, Action]): RIO[Reader, Action] =
    if (actions.size == 0)
      Task.fail(new java.util.NoSuchElementException("No actions to pick from"))
    else {
      (for {
        reader <- ZIO.environment[Reader]
        key    <- reader.readOption(s"Choose action: ", actions.keys.toSeq).map(_.toLowerCase)
        action <- Task(actions(key))
      } yield action).catchAll(err => putStrLn(s"Error: ${err.getMessage}") *> pickAction(actions))
    }

  def chooseAction(input: Any): RIO[ScalarrEnvironment, Any] =
    (for {
      reader    <- ZIO.access[ScalarrEnvironment](_.reader)
      sonarr    <- ZIO.access[ScalarrEnvironment](_.sonarr)
      children  <- childrenOf(input).provide(sonarr)
      actions   <- actionsOf(input).provide(sonarr).map(_ :+ Action(key"{q}uit", Task.unit))
      optionMap = (children ++ actions).map(action => action.key.key -> action).toMap
      _         <- showActions(children, actions)
      action    <- pickAction(optionMap).provide(reader)
      _         <- putStrLn(s"Chosen: ${action.key}")
    } yield action.task).flatten
}

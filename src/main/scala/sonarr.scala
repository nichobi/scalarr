package scalarr

import scalarr.util.formatting.monitoredSymbol
import com.softwaremill.sttp._
import scala.util.{Failure, Success, Try}
import util.art.imgConvert
import cats.Show
import zio._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization._
import org.json4s.CustomSerializer

object sonarr {
  class Sonarr(val address: String, val port: Int, apiKey: String) {

    val base                    = uri"$address:$port"
    implicit val backend        = HttpURLConnectionBackend()
    implicit val defaultFormats = DefaultFormats + new SeriesSerializer

    def asExtracted[A](implicit m: Manifest[A]): ResponseAs[Task[A], Nothing] =
      asString.map(extractJson[A])
    def extractJson[A](json: String)(implicit m: Manifest[A]): Task[A] =
      Task(parse(json)).map(_.extract[A])

    class SeriesSerializer
        extends CustomSerializer[Series](_ =>
          ({
            case x: JObject =>
              val id = (x \ "id").extract[Option[Int]]
              id match {
                case Some(_) =>
                  x.extract[AddedSeries]
                case None =>
                  x.extract[LookupSeries]
              }
          }, {
            case x: Series =>
              parse(write(x))
          }))

    def get[A](endpoint: String, params: (String, String)*)(implicit m: Manifest[A]): Task[A] = {
      val request = sttp
        .get(
          base
            .path(s"api/$endpoint")
            .params(params.toMap)
        )
        .header("X-Api-Key", apiKey)
        .response(asExtracted[A])
      val response = request.send()
      Task(response.unsafeBody).flatten
    }

    def post(endpoint: String, body: JValue): Task[JValue] = {
      val request = sttp
        .post(base.path(s"api/$endpoint"))
        .body(pretty(render(body)))
        .header("X-Api-Key", apiKey)
        .response(asExtracted[JValue])
      val response = request.send()
      Task(response.unsafeBody).flatten
    }

    def put(endpoint: String, body: JValue): Task[JValue] = {
      val request = sttp
        .put(base.path(s"api/$endpoint"))
        .body(pretty(render(body)))
        .header("X-Api-Key", apiKey)
        .response(asExtracted[JValue])
      val response = request.send()
      Task(response.unsafeBody).flatten
    }

    def lookup(query: String, resultSize: Int = 5): Task[Seq[Series]] =
      get[List[JValue]]("series/lookup", ("term", query))
        .map(_.take(resultSize).map(_.extract[Series]))

    def allSeries: Task[Seq[AddedSeries]] =
      get[List[AddedSeries]]("series")

    def series(id: Int): Task[AddedSeries] =
      get[AddedSeries](s"series/$id")

    def seasons(series: AddedSeries): Task[Seq[Season]] = {
      def getSeasonInfo(i: Int) = {
        series.seasons.find(_.seasonNumber == i).get
      }
      def groupIntoSeasons(eps: Seq[Episode]) =
        eps
          .groupBy(_.seasonNumber)
          .map {
            case seasonNumber -> episodes =>
              val info = getSeasonInfo(seasonNumber)
              Season(seasonNumber, episodes, series.id, info.monitored)
          }
          .toSeq
          .sortBy(_.n)

      for {
        episodes <- get[List[Episode]]("episode", ("seriesId", series.id.toString))
        seasons  = groupIntoSeasons(episodes)
      } yield seasons
    }

    def posterUrl(series: Series): Try[Uri] = series.posterPath match {
      case Success(path) if (path.startsWith("/")) =>
        Try {
          val patchedPath = if (path.contains("/poster.")) {
            path.patch(path.indexOf("/poster.") + 7, "-250", 0)
          } else path
          uri"$base".path(s"/api$patchedPath").params(("apikey", apiKey))
        }
      case Success(tvdbUrl) =>
        Try {
          val patchedUrl = if (tvdbUrl.contains("/posters/")) {
            tvdbUrl.patch(tvdbUrl.indexOf("/posters/"), "/_cache", 0)
          } else tvdbUrl
          uri"$patchedUrl"
        }
      case Failure(x) => Failure(x)
    }

    def poster(series: Series): Try[String] = posterUrl(series).flatMap(url => imgConvert(url))

    def posterOrBlank(series: Series): String = poster(series).getOrElse("      \n" * 4)

    def seriesSearch(
        query: String,
        resultSize: Int = 5
    ): Task[Seq[AddedSeries]] =
      allSeries
        .map(_.filter(_.title.toLowerCase.contains(query)))
        .map(_.take(resultSize))

    def add(
        series: Series,
        rootFolder: RootFolder,
        qualityProfile: Profile
    ): Task[JValue] =
      series match {
        case series: LookupSeries =>
          for {
            //Perform a lookup of the tvdbId to get the full json file for the series
            lookupJson <- get[List[JValue]]("series/lookup", ("term", s"tvdb:${series.tvdbId}"))
                           .map(_.head)
            extraParams = parse(s"""{
                                   |  "rootFolderPath": "${rootFolder.path}"
                                   |  "qualityProfileId": ${qualityProfile.id}
                                   |}""".stripMargin)
            body        = lookupJson.merge(extraParams)
            result      <- post("series", body)
          } yield result
        case _ => Task.fail(new Exception("Series already exists"))
      }

    def importPath(path: os.Path, copy: Boolean): Task[JValue] = {
      val importMode = if (copy) "Copy" else "Move"
      val body = ("name" -> "DownloadedEpisodesScan") ~
        ("importMode" -> importMode) ~
        ("path"       -> path.toString)
      post("command", body)
    }

    def remove(series: Series) = ???

    def searchSeason(season: Season)    = ???
    def searchEpisode(episode: Episode) = ???

    def profiles    = get[List[Profile]]("profile")
    def rootFolders = get[List[RootFolder]]("rootfolder")
    def diskSpace   = get[List[DiskSpace]]("diskspace")
    def version: Task[String] =
      for {
        status  <- get[Map[String, JValue]]("system/status")
        version = status("version").extract[String]
      } yield version

    override def toString = s"""Scalarr($address, $port, REDACTED)"""
  }

  object Sonarr {
    def apply(address: String, port: Int, apiKey: String) = new Sonarr(address, port, apiKey)
  }

  sealed trait Series {
    val tvdbId: Int
    val title: String
    val year: Int
    val status: String
    val seasonCount: Int
    val images: List[Map[String, String]]
    lazy val posterPath = Try {
      images
        .filter(_("coverType") == "poster")
        .head("url")
        .takeWhile(_ != '?')
    }

    override def toString = s"$title ($year) - tvdb:$tvdbId"
  }
  object Series {
    implicit val showSeries: Show[Series] =
      Show.show(s => s"${s.title} (${s.year}) - tvdb:${s.tvdbId}")
  }

  final case class AddedSeries(
      tvdbId: Int,
      title: String,
      year: Int,
      status: String,
      seasonCount: Int,
      images: List[Map[String, String]],
      id: Int,
      monitored: Boolean,
      seasons: Seq[SeasonInfo]
  ) extends Series {
    override def toString = s"${monitoredSymbol(monitored)} $title ($year) - id:$id"
  }
  object AddedSeries {
    implicit val showAddedSeries: Show[AddedSeries] =
      Show.show(s => s"${s.title} (${s.year}) - id:${s.id}")
  }
  case class SeasonInfo(seasonNumber: Int, monitored: Boolean, statistics: SeasonStatistics)
  case class SeasonStatistics(
      episodeFileCount: Int,
      episodeCount: Int,
      totalEpisodeCount: Int,
      sizeOnDisk: Int,
      percentOfEpisodes: Int
  )

  final case class LookupSeries(
      tvdbId: Int,
      title: String,
      year: Int,
      status: String,
      seasonCount: Int,
      images: List[Map[String, String]]
  ) extends Series

  final case class Episode(seasonNumber: Int,
                           episodeNumber: Int,
                           title: String,
                           id: Int,
                           monitored: Boolean) {
    override def toString =
      s"""${monitoredSymbol(monitored)} S${f"$seasonNumber%02d"}E${f"$episodeNumber%02d"} - $title"""
  }

  object Episode {
    implicit val showEpiosde: Show[Episode] = Show.fromToString
  }

  final case class Season(n: Int, eps: Seq[Episode], seriesId: Int, monitored: Boolean) {
    val name              = if (n == 0) "Specials" else s"Season $n"
    override def toString = s"${monitoredSymbol(monitored)} $name"
  }
  object Season {
    implicit val showSeason: Show[Season] = Show.fromToString
  }

  final case class DiskSpace(path: String, freeSpace: Double, totalSpace: Double) {
    override def toString = s"$path: $percentUsed% used"
    def percentUsed       = ((1 - freeSpace / totalSpace) * 100 + 0.5).toInt
  }
  object DiskSpace {
    implicit val showDiskSpace: Show[DiskSpace] =
      Show.show(ds => s"${ds.path}: ${ds.percentUsed}% used")
  }

  final case class Profile(id: Int, name: String) {
    override def toString = name
  }
  object Profile {
    implicit val showProfile: Show[Profile] = Show.show(_.name)
  }

  final case class RootFolder(id: Int, path: String) {
    override def toString = path
  }
  object RootFolder {
    implicit val showRootFolder: Show[RootFolder] = Show.show(_.path)
  }
}

package scalarr
import com.softwaremill.sttp._
import scala.util.{Failure, Success, Try}
import cats.Show
import zio._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization._
import org.json4s.CustomSerializer

case class Sonarr(address: String, port: Int, apiKey: String) {

  val base                    = uri"$address:$port"
  implicit val backend        = HttpURLConnectionBackend()
  implicit val defaultFormats = DefaultFormats + new SeriesSerializer

  val asJson: ResponseAs[Try[JValue], Nothing] = asString.map(parseJson)
  def parseJson(json: String): Try[JValue]     = Try(parse(json))

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

  def get(endpoint: String, params: (String, String)*): Task[JValue] =
    Task.fromTry {
      val request = sttp
        .get(
          base
            .path(s"api/$endpoint")
            .params(params.toMap)
        )
        .header("X-Api-Key", apiKey)
        .response(asJson)
      val response = request.send()
      response.unsafeBody
    }

  def post(endpoint: String, body: JValue): Task[JValue] =
    Task.fromTry {
      val request = sttp
        .post(base.path(s"api/$endpoint"))
        .body(pretty(render(body)))
        .header("X-Api-Key", apiKey)
        .response(asJson)
      val response = request.send()
      response.unsafeBody
    }

  def lookup(query: String, resultSize: Int = 5): Task[Seq[Series]] = {
    get("series/lookup", ("term", query))
      .map(_.extract[List[JValue]].take(resultSize).map(_.extract[Series]))
  }

  def allSeries: Task[Seq[AddedSeries]] =
    get("series").map(_.extract[List[AddedSeries]])

  def series(id: Int): Task[AddedSeries] =
    get(s"series/$id").map(_.extract[AddedSeries])

  def seasons(series: AddedSeries): Task[Seq[Season]] = {
    def groupIntoSeasons(eps: Seq[Episode]) = {
      eps.groupBy(_.seasonNumber).map(x => Season(x._1, x._2)).toSeq.sortBy(_.n)
    }

    for {
      json     <- get("episode", ("seriesId", series.id.toString))
      episodes = json.extract[List[Episode]]
      seasons  = groupIntoSeasons(episodes)
    } yield seasons
  }

  def seriesSearch(
      query: String,
      resultSize: Int = 5
  ): Task[Seq[AddedSeries]] = {
    allSeries
      .map(_.filter(_.title.toLowerCase.contains(query)))
      .map(_.take(resultSize))
  }

  def add(
      series: Series,
      rootFolder: RootFolder,
      qualityProfile: Profile
  ): Task[JValue] = {
    series match {
      case series: LookupSeries =>
        for {
          //Perform a lookup of the tvdbId to get the full json file for the series
          lookupJson  <- get("series/lookup", ("term", s"tvdb:${series.tvdbId}")).map(_(0))
          extraParams = parse(s"""{
            "rootFolderPath": "${rootFolder.path}"
            "qualityProfileId": ${qualityProfile.id}
          }""")
          body        = lookupJson merge extraParams
          result      <- post("series", body)
        } yield result
      case _ => Task.fail(new Exception("Series already exists"))
    }
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

  def poster(series: Series): Try[String] = {
    posterUrl(series).flatMap(url => util.imgConvert(url))
  }

  def posterOrBlank(series: Series): String = {
    val blankPoster: String = "      \n" * 4
    poster(series).getOrElse(blankPoster)
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

  def profiles    = get("profile").map(_.extract[List[Profile]])
  def rootFolders = get("rootfolder").map(_.extract[List[RootFolder]])
  def diskSpace   = get("diskspace").map(_.extract[List[DiskSpace]])
  def version: Task[String] =
    for {
      status  <- get("system/status").map(_.extract[Map[String, JValue]])
      version = status("version").extract[String]
    } yield version
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
    id: Int
) extends Series {

  override def toString = s"$title ($year) - id:$id"
}
object AddedSeries {
  implicit val showAddedSeries: Show[AddedSeries] =
    Show.show(s => s"${s.title} (${s.year}) - id:${s.id}")
}

final case class LookupSeries(
    tvdbId: Int,
    title: String,
    year: Int,
    status: String,
    seasonCount: Int,
    images: List[Map[String, String]]
) extends Series

case class Episode(seasonNumber: Int, episodeNumber: Int, title: String) {
  override def toString =
    s"""S${f"$seasonNumber%02d"}E${f"$episodeNumber%02d"} - $title"""
}

object Episode {
  implicit val showEpiosde: Show[Episode] = Show.fromToString
}

case class Season(n: Int, eps: Seq[Episode]) {
  override def toString = if (n == 0) "Specials" else s"Season $n"
}
object Season {
  implicit val showSeason: Show[Season] = Show.fromToString
}

case class DiskSpace(path: String, freeSpace: Double, totalSpace: Double) {
  override def toString = s"$path: $percentUsed% used"
  def percentUsed       = ((1 - freeSpace / totalSpace) * 100 + 0.5).toInt
}
object DiskSpace {
  implicit val showDiskSpace: Show[DiskSpace] =
    Show.show(ds => s"${ds.path}: ${ds.percentUsed}% used")
}

case class Profile(id: Int, name: String) {
  override def toString = name
}
object Profile {
  implicit val showProfile: Show[Profile] = Show.show(_.name)
}

case class RootFolder(id: Int, path: String) {
  override def toString = path
}
object RootFolder {
  implicit val showRootFolder: Show[RootFolder] = Show.show(_.path)
}

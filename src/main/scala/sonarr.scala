package scalarr
import com.softwaremill.sttp._
import scala.util.{Try, Success, Failure}
import cats.Show
import zio._

case class Sonarr(address: String, port: Int, apiKey: String) {

  val base                                          = uri"$address:$port"
  implicit val backend                              = HttpURLConnectionBackend()
  val asJson: ResponseAs[Try[ujson.Value], Nothing] = asString.map(parseJson)
  def parseJson(json: String): Try[ujson.Value]     = Try(ujson.read(json))

//  implicit val episodeReads = Json.reads[Episode]

  def get(endpoint: String, params: (String, String)*): Task[ujson.Value] =
    IO.fromTry {
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

  def post(endpoint: String, body: ujson.Value): Task[ujson.Value] =
    IO.fromTry {
      val request = sttp
        .post(base.path(s"api/$endpoint"))
        .body(body.render())
        .header("X-Api-Key", apiKey)
        .response(asJson)
      val response = request.send()
      response.unsafeBody
    }

  def lookup(query: String, resultSize: Int = 5): Task[Seq[Series]] = {
    get("series/lookup", ("term", query))
      .map(_.arr.take(resultSize).toSeq.map(x => Series(x)))
  }

  def allSeries: Task[Seq[AddedSeries]] =
    get("series") map (_.arr.toSeq.map(json => AddedSeries(json)))

  def series(id: Int): Task[AddedSeries] =
    get(s"series/$id").map(json => AddedSeries(json))

  def seasons(series: AddedSeries): Task[Seq[Season]] = {
    val episodes = get("episode", ("seriesId", series.id.toString))
      .map(_.arr.toSeq.map(ep => Episode(ep)))

    def episodesToSeasons(eps: Seq[Episode]) = {
      eps.groupBy(_.seasonNumber).map(x => Season(x._1, x._2))
    }

    episodes.map(eps => episodesToSeasons(eps).toSeq.sortBy(_.n))
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
      rootPath: RootFolder,
      qualityProfile: Profile
  ): Task[ujson.Value] = {
    series match {
      case series: LookupSeries =>
        val params = series.json
        params("rootFolderPath") = rootPath.path
        params("qualityProfileId") = qualityProfile.id
        post("series", params)
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

  def importPath(path: os.Path, copy: Boolean): Task[ujson.Value] = {
    val importMode = if (copy) "Copy" else "Move"
    val body = ujson.Obj(
      "name"       -> "DownloadedEpisodesScan",
      "importMode" -> importMode,
      "path"       -> path.toString
    )
    post("command", body)
  }

  def remove(series: Series) = ???

  def searchSeason(season: Season)    = ???
  def searchEpisode(episode: Episode) = ???

  def profiles = get("profile").map(_.arr.map(json => Profile(json)).toSeq)
  def rootFolders =
    get("rootfolder").map(_.arr.map(json => RootFolder(json)).toSeq)
  def version   = get("system/status").map(_("version").str)
  def diskSpace = get("diskspace").map(_.arr.map(json => DiskSpace(json)).toSeq)

}

abstract class Series(val json: ujson.Value) {
  val tvdbId      = json("tvdbId").num.toInt
  val title       = json("title").str
  val year        = json("year").num.toInt
  val status      = json("status").str
  val seasonCount = json("seasonCount").num.toInt
  val posterPath: Try[String] = Try {
    json("images").arr
      .map(_.obj)
      .filter(_("coverType").str == "poster")
      .head("url")
      .str
      .takeWhile(_ != '?')
  }

  override def toString = s"$title ($year) - tvdb:$tvdbId"
}
object Series {
  def apply(json: ujson.Value): Series = {
    if (json.obj.contains("id")) new AddedSeries(json)
    else new LookupSeries(json)
  }

  implicit val showSeries: Show[Series] =
    Show.show(s => s"${s.title} (${s.year}) - tvdb:${s.tvdbId}")
}

class AddedSeries(json: ujson.Value) extends Series(json) {
  val id = json("id").num.toInt

  override def toString = s"$title ($year) - id:$id"
}
object AddedSeries {
  def apply(json: ujson.Value) = new AddedSeries(json)
  implicit val showAddedSeries: Show[AddedSeries] =
    Show.show(s => s"${s.title} (${s.year}) - id:${s.id}")
}

class LookupSeries(json: ujson.Value) extends Series(json) {}
object LookupSeries {
  def apply(json: ujson.Value) = new LookupSeries(json)
}

case class Episode(json: ujson.Value) {
  val seasonNumber  = json("seasonNumber").num.toInt
  val episodeNumber = json("episodeNumber").num.toInt
  val title         = json("title").str
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

case class DiskSpace(json: ujson.Value) {
  val path       = json("path").str
  val freeSpace  = json("freeSpace").num
  val totalSpace = json("totalSpace").num

  override def toString = s"$path: $percentUsed% used"
  def percentUsed       = ((1 - freeSpace / totalSpace) * 100 + 0.5).toInt
}
object DiskSpace {
  implicit val showDiskSpace: Show[DiskSpace] =
    Show.show(ds => s"${ds.path}: ${ds.percentUsed}% used")
}

case class Profile(json: ujson.Value) {
  val id                = json("id").num.toInt
  val name              = json("name").str
  override def toString = name
}
object Profile {
  implicit val showProfile: Show[Profile] = Show.show(_.name)
}

case class RootFolder(json: ujson.Value) {
  val id                = json("id").num.toInt
  val path              = json("path").str
  override def toString = path
}
object RootFolder {
  implicit val showRootFolder: Show[RootFolder] = Show.show(_.path)
}

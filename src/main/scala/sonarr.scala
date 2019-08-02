package scalarr
import com.softwaremill.sttp._
import ujson._
import scala.util.control.Exception.allCatch
import scala.util.{Try,Success,Failure}

case class Sonarr(address: String, port: Int, apiKey: String){

  val base = Uri(address).port(port).path("/api")
  implicit val backend = HttpURLConnectionBackend()
  val asJson: ResponseAs[Try[ujson.Value], Nothing] = asString.map(parseJson)
  def parseJson(json: String): Try[ujson.Value] = Try(ujson.read(json))

  def get( endpoint: String, params: (String, String)*): Try[ujson.Value] = {
    val request = sttp.get(base.path(s"api/$endpoint")
      .params(params.toMap))
      .header("X-Api-Key", apiKey)
      .response(asJson)
    val response = request.send()
    response.unsafeBody
  }

  def post(endpoint: String, body: ujson.Value): Try[ujson.Value] = {
    val request = sttp.post(base.path(s"api/$endpoint"))
      .body(body.render())
      .header("X-Api-Key", apiKey)
      .response(asJson)
    val response = request.send()
    response.unsafeBody
  }

  def lookup(query: String, resultSize: Int = 5): Try[Seq[Series]] = {
    get("series/lookup", ("term", query)).map(
      _.arr.take(resultSize).toSeq.map(x => Series(x)))
  }

  def allSeries = get("series").map(_.arr.toSeq.map(json => AddedSeries(json)))

  def series(id: Int) = get(s"series/$id").map(json => AddedSeries(json))

  def getEpisodes(id: Int): Try[Seq[Season]] = {
    val episodes = get("episode", ("seriesId", id.toString)).map(
      _.arr.toSeq.map(ep => Episode(ep)))
    
    def episodesToSeasons(eps: Seq[Episode]) = {
      eps.groupBy(_.seasonNumber).map(x => Season(x._1, x._2))
    }

    episodes.map(eps => episodesToSeasons(eps).toSeq.sortBy(_.n))
  }

  def seriesSearch(query: String, resultSize: Int = 5): Try[Seq[AddedSeries]] = {
    allSeries.map(_.filter(_.title.toLowerCase.contains(query)))
  }

  def profiles = get("profile").map(_.arr.map(json => Profile(json)).toSeq)
  def rootFolders = get("rootfolder").map(_.arr.map(json => RootFolder(json)).toSeq)
  def version = get("system/status").map(_("version").str)
  def diskSpace = get("diskspace").map(_.arr.map(json => DiskSpace(json)).toSeq)
}

abstract class Series(val json: ujson.Value) {
  val tvdbId = json("tvdbId").num.toInt
  val title = json("title").str
  val year = json("year").num.toInt
  val status = json("status").str
  val seasonCount = json("seasonCount").num.toInt

  override def toString = s"$title ($year) - tvdb:$tvdbId"
}
object Series {
  def apply(json: ujson.Value) = {
    if(json.obj.contains("id")) new AddedSeries(json)
    else new LookupSeries(json)
  }
}

class AddedSeries(json: ujson.Value) extends Series(json) {
  val id = json("id").num.toInt

  override def toString = s"$title ($year) - id:$id"
}
object AddedSeries {
  def apply(json: ujson.Value) = new AddedSeries(json)
}

class LookupSeries(json: ujson.Value) extends Series(json) {
}
object LookupSeries {
  def apply(json: ujson.Value) = new LookupSeries(json)
}

case class Episode(json: ujson.Value) {
  val seasonNumber = json("seasonNumber").num.toInt
  val episodeNumber = json("episodeNumber").num.toInt
  val title = json("title").str

  override def toString =
    s"""S${f"$seasonNumber%02d"}E${f"$episodeNumber%02d"} - $title"""
}

case class Season(n: Int, eps: Seq[Episode]){
  override def toString = if(n == 0) "Specials" else s"Season $n"
}

case class DiskSpace(json: ujson.Value) {
  val path = json("path").str
  val freeSpace = json("freeSpace").num
  val totalSpace = json("totalSpace").num

  override def toString = s"$path: $percentUsed% used"
  def percentUsed = ((1 - freeSpace / totalSpace) * 100 + 0.5).toInt
}

case class Profile(json: ujson.Value) {
  val id = json("id").num.toInt
  val name = json("name").str
  override def toString = name
}

case class RootFolder(json: ujson.Value) {
  val id = json("id").num.toInt
  val path = json("path").str
  override def toString = path
}

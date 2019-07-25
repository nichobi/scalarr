package scalarr
import com.softwaremill.sttp._
import ujson._
import scala.util.control.Exception.allCatch

case class Sonarr(address: String, port: Int, apiKey: String){

  val base = Uri(address).port(port).path("/api")
  implicit val backend = HttpURLConnectionBackend()

  def get(endpoint: String, params: (String, String)*): ujson.Value = {
    val request = sttp.get(base.path(s"api/$endpoint").params(params.toMap))
      .header("X-Api-Key", apiKey)
    val response = request.send()
    val parsed = ujson.read(response.unsafeBody)
    parsed
  }

  def lookup(query: String, resultSize: Int = 5): Seq[Series] = {
    get("series/lookup", ("term", query)).arr.take(resultSize).toSeq
      .map(x => Series(x))
  }

  def allSeries = get("series").arr.toSeq.map(x => AddedSeries(x))

  def series(id: Int) = AddedSeries(get(s"series/$id"))

  def getEpisodes(id: Int): Seq[Season] = {
    val episodes = get("episode", ("seriesId", id.toString)).arr.toSeq
      .map(ep => Episode(ep))
    
    def episodesToSeasons(eps: Seq[Episode]) = {
      eps.groupBy(_.seasonNumber).map(x => Season(x._1, x._2))
    }

    episodesToSeasons(episodes).toSeq.sortBy(_.n)
  }

  def seriesSearch(query: String, resultSize: Int = 5): Seq[AddedSeries] = {
    allSeries.filter(_.title.toLowerCase.contains(query))
  }

  def version = get("system/status")("version").str
  def diskSpace = get("diskspace").arr.map(json => DiskSpace(json))
}

class Series(json: ujson.Value) {
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
  def add(path: String) = ???
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

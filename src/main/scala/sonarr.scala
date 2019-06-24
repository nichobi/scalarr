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

  def allSeries = get("series").arr.toSeq.map(x => Series(x))

  def series(id: Int) = Series(get(s"series/$id"))

 def getEpisodes(id: Int): Map[Int, Seq[Episode]] = 
   get("episode", ("seriesId", id.toString)).arr.toSeq.map(ep => Episode(ep))
     .groupBy(ep => ep.seasonNumber)
}

case class Series(json: ujson.Value) {
  val tvdbId = json("tvdbId").num.toInt
  val id = allCatch.opt(json("id").num.toInt)
  val title = json("title").str
  val year = json("year").num.toInt
  val status = json("status").str
  val seasonCount = json("seasonCount").num.toInt

  override def toString = s"$title ($year) - ${id getOrElse tvdbId}"
}

case class Episode(json: ujson.Value) {
  val seasonNumber = json("seasonNumber").num.toInt
  val episodeNumber = json("episodeNumber").num.toInt
  val title = json("title").str

  override def toString = 
    s"""S${f"$seasonNumber%02d"}E${f"$episodeNumber%02d"} - $title"""
} 


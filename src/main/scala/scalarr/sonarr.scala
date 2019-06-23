package scalarr
import com.softwaremill.sttp._
import ujson._
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

}

case class Series(json: ujson.Value) {
  val tvdbId = json("tvdbId").num.toInt
  val title = json("title").str
  val year = json("year").num.toInt
  val status = json("status").str
  val seasonCount = json("seasonCount").num.toInt

  override def toString = s"$title ($year) - $tvdbId"
  def formatted = s"""$title - $year
    |  $status - Seasons: $seasonCount""".stripMargin
}

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

  def search(query: String) = {
    val request = sttp
      .get(uri"$base/series/lookup")
      .header("X-Api-Key", apiKey)
    val response = request.send()
    println(response)
  }

  def formatSeries(seriesList: ujson.Value): Seq[String] = {
    for(i <- seriesList.arr.indices) yield{
      val series = seriesList(i)
      s"""($i) ${series("title").str} - ${series("year").num.toInt}
     |  ${series("status").str} - Seasons: ${series("seasonCount").num.toInt} """.stripMargin
    }
  }
}

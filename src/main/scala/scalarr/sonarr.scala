package scalarr
import com.softwaremill.sttp._
case class Sonarr(address: String, apiKey: String){

  val base = address + "/api"
  implicit val backend = HttpURLConnectionBackend()
  def search(query: String) = {
    val request = sttp
      .get(uri"$base/series/lookup")
      .header("X-Api-Key", apiKey)
    val response = request.send()
    println(response)
  }
}

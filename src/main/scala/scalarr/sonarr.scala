package scalarr
import com.softwaremill.sttp._
case class Sonarr(address: String, port: Int, apiKey: String){

  val base = Uri(address).port(port).path("/api")
  implicit val backend = HttpURLConnectionBackend()
  def search(query: String) = {
    val request = sttp
      .get(uri"$base/series/lookup")
      .header("X-Api-Key", apiKey)
    val response = request.send()
    println(response)
  }
}

package scalarr

import com.softwaremill.sttp._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization._
import scala.util.{Failure, Success, Try}
import scalarr.util.formatting.monitoredSymbol
import scalarr.util.show._
import scalarr.util.art.imgConvert
import zio._

object radarr {
  class Radarr(val address: String, val port: Int, apiKey: String) {

    val base                    = uri"$address:$port"
    implicit val backend        = HttpURLConnectionBackend()
    implicit val defaultFormats = DefaultFormats

    def asExtracted[A](implicit m: Manifest[A]): ResponseAs[Task[A], Nothing] =
      asString.map(extractJson[A])
    def extractJson[A](json: String)(implicit m: Manifest[A]): Task[A] =
      Task(parse(json)).map(_.extract[A])

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

    def lookup(query: String, resultSize: Int = 5): Task[Seq[Movie]] =
      get[List[JValue]]("movie/lookup", ("term", query))
        .map(_.take(resultSize).map(_.extract[LookupMovie]))

    def allMovies: Task[Seq[AddedMovie]] =
      get[List[AddedMovie]]("movie")

    def movie(id: Int): Task[AddedMovie] =
      get[AddedMovie](s"movie/$id")

    def posterUrl(movie: Movie): Try[Uri] = movie.posterPath match {
      case Success(path) if (path.startsWith("/")) =>
        Try {
          val patchedPath = if (path.contains("/poster.")) {
            path.patch(path.indexOf("/poster.") + 7, "-250", 0)
          } else path
          uri"$base".path(s"/api$patchedPath").params(("apikey", apiKey))
        }
      case Success(otherUrl) => println(s"Nonlocal url: $otherUrl")
        Success(uri"otherUrl")
        //Try {
        //  val patchedUrl = if (tvdbUrl.contains("/posters/")) {
        //    tvdbUrl.patch(tvdbUrl.indexOf("/posters/"), "/_cache", 0)
        //  } else tvdbUrl
        //  uri"$patchedUrl"
        //}
      case Failure(x) => Failure(x)
    }

    def poster(movie: Movie): Task[String] =
      Task.fromTry(posterUrl(movie)).flatMap(url => imgConvert(url))

    def posterOrBlank(movie: Movie): Task[String] =
      poster(movie).orElse(Task.succeed("      \n" * 4))

    def movieSearch(
        query: String,
        resultSize: Int = 5
    ): Task[Seq[AddedMovie]] =
      allMovies
        .map(_.filter(_.title.toLowerCase.contains(query)))
        .map(_.take(resultSize))

    def add(
        movie: Movie,
        rootFolder: RootFolder,
        qualityProfile: Profile
    ): Task[JValue] =
      movie match {
        case movie: LookupMovie =>
          for {
            //Perform a lookup of the tvdbId to get the full json file for the movie
            lookupJson <- get[List[JValue]]("movie/lookup/tmdb", ("tmdbId", movie.tmdbId.toString)).map(_.head)
            extraParams = parse(s"""{
                                   |  "rootFolderPath": "${rootFolder.path}"
                                   |  "qualityProfileId": ${qualityProfile.id}
                                   |}""".stripMargin)
            body        = lookupJson.merge(extraParams)
            result      <- post("movie", body)
          } yield result
        case _ => Task.fail(new Exception("Movie already exists"))
      }

    def importPath(path: os.Path, copy: Boolean): Task[JValue] = {
      val importMode = if (copy) "Copy" else "Move"
      val body = ("name" -> "DownloadedMovieScan") ~
        ("importMode" -> importMode) ~
        ("path"       -> path.toString)
      post("command", body)
    }

    def remove(movie: Movie): Task[Unit] = Task(???)

    def search(movie: AddedMovie): Task[JValue] = {
      val body = ("name" -> "MoviesSearch") ~
        ("movieIds" -> List(movie.id))
      post("command", body)
    }

    def setMonitored(movie: AddedMovie, monitor: Boolean): Task[Unit] =
      for {
        movie       <- get[JValue](s"movie/${movie.id}")
        movieEdited <- Task(movie.replace("monitored" :: Nil, JBool(monitor)))
        _            <- put("movie", movieEdited)
      } yield ()

    def profiles    = get[List[Profile]]("profile")
    def rootFolders = get[List[RootFolder]]("rootfolder")
    def diskSpace   = get[List[DiskSpace]]("diskspace")
    def version: Task[String] =
      for {
        status  <- get[Map[String, JValue]]("system/status")
        version = status("version").extract[String]
      } yield version

    override def toString = s"""Radarr($address, $port, REDACTED)"""
  }

  object Radarr {
    def apply(address: String, port: Int, apiKey: String) = new Radarr(address, port, apiKey)
  }

  sealed trait Movie {
    val tmdbId: Int
    val title: String
    val year: Int
    val images: List[Map[String, String]]
    lazy val posterPath = Try {
      images
        .filter(_("coverType") == "poster")
        .head("url")
        .takeWhile(_ != '?')
    }

    override def toString = s"$title ($year) - tmdb:$tmdbId"
  }
  object Movie {
    implicit val showMovie: Show[Movie] =
      Show(s => s"${s.title} (${s.year}) - tmdb:${s.tmdbId}")
  }

  final case class AddedMovie(
      tmdbId: Int,
      title: String,
      year: Int,
      images: List[Map[String, String]],
      id: Int,
      monitored: Boolean,
  ) extends Movie {
    override def toString = s"${monitoredSymbol(monitored)} $title ($year) - id:$id"
  }
  object AddedMovie {
    implicit val showAddedMovie: Show[AddedMovie] =
      Show(s => s"${s.title} (${s.year}) - id:${s.id}")
  }

  final case class LookupMovie(
      tmdbId: Int,
      title: String,
      year: Int,
      images: List[Map[String, String]]
  ) extends Movie

  final case class DiskSpace(path: String, freeSpace: Double, totalSpace: Double) {
    override def toString = s"$path: $percentUsed% used"
    def percentUsed       = ((1 - freeSpace / totalSpace) * 100 + 0.5).toInt
  }
  object DiskSpace {
    implicit val showDiskSpace: Show[DiskSpace] =
      Show(ds => s"${ds.path}: ${ds.percentUsed}% used")
  }

  final case class Profile(id: Int, name: String) {
    override def toString = name
  }
  object Profile {
    implicit val showProfile: Show[Profile] = Show(_.name)
  }

  final case class RootFolder(id: Int, path: String) {
    override def toString = path
  }
  object RootFolder {
    implicit val showRootFolder: Show[RootFolder] = Show(_.path)
  }
}

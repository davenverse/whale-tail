package io.chrisdavenport.whaletail

import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s.client.Client

import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.implicits._
import io.circe.syntax._
import cats.ApplicativeThrow

object Images {
  object Operations {

    private val imagesPrefix = (baseUri: Uri) =>  baseUri / "images"

    def list[F[_]: Concurrent](
      client: Client[F],
      all: Boolean = false,
      filters: Map[String, List[String]] = Map.empty,
      digests: Boolean = false,
      baseUri: Uri = Docker.versionPrefix
    ): F[Json] = {
      val uriI = imagesPrefix(baseUri) / "json"
      val uri = uriI.withQueryParam("all", all)
        .withQueryParam("filters", filters.asJson.printWith(Printer.noSpaces))
        .withQueryParam("digests", digests)
      val req = Request[F](Method.GET, uri)
      client.run(req).use(resp => 
        if (resp.status === Status.Ok) resp.asJson
        else Data.ImagesErrorResponse.raise(req, resp)
      )
    }

    def createFromImage[F[_]: Concurrent](//Bracket[*[_], Throwable]: JsonDecoder](
      client: Client[F],
      image: String,
      tag: Option[String] = None,
      baseUri: Uri = Docker.versionPrefix
    ): F[String] = {
      val req = Request[F](
        Method.POST, 
        (imagesPrefix(baseUri) / "create").setQueryParams(Map(
            "fromImage" -> Seq(image),
            "tag" ->  tag.toSeq
        ))
      )
      client.run(req).use{resp => 
        if (resp.status === Status.Ok) resp.bodyText.compile.string
        else Data.ImagesErrorResponse.raise(req, resp)
      }
  }
  }

  object Data {
    final case class ImagesErrorResponse(req: RequestPrelude, resp: ResponsePrelude, body: String) 
      extends RuntimeException(show"Images Response Not Expected for Request: $req -  Status: ${resp.status}, headers: ${resp.headers}, httpVersion: ${resp.httpVersion}, body:$body")
    object ImagesErrorResponse {
      def raise[F[_]: Concurrent, A](req: Request[F], resp: Response[F]): F[A] = 
        resp.bodyText.compile.string.flatMap{ body  => 
          ApplicativeThrow[F].raiseError(ImagesErrorResponse(req.requestPrelude, resp.responsePrelude, body))
        }
    }
  }
}
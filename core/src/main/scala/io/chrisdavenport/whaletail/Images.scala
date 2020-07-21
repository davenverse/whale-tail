package io.chrisdavenport.whaletail

import cats.effect._
import org.http4s.client.Client

import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.implicits._
import io.circe.syntax._

object Images {
  object Operations {

    private val imagesPrefix = Docker.versionPrefix / "images"

    def createFromImage[F[_]: Sync](//Bracket[*[_], Throwable]: JsonDecoder](
      client: Client[F],
      image: String,
      tag: Option[String] = None
    ): F[String] = client.run(
      Request(
        Method.POST, 
        (imagesPrefix / "create").setQueryParams(Map(
            "fromImage" -> Seq(image),
            "tag" ->  tag.toSeq
        ))
      )
    ).use{resp => resp.bodyText.compile[F, F, String].string}
  }
}
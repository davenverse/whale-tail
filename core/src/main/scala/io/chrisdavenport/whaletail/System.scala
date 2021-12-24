package io.chrisdavenport.whaletail

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import io.circe._
import org.http4s.circe._

object System {
  object Operations {
    def info[F[_]: Concurrent](client: Client[F], 
      baseUri: Uri = Docker.versionPrefix
    ): F[Json] = 
      client.expect[Json](Request[F](Method.GET, baseUri / "info"))

    def version[F[_]: Concurrent](client: Client[F],
      baseUri: Uri = Docker.versionPrefix): F[Json] = 
      client.expect[Json](Request[F](Method.GET, baseUri / "version"))

    def ping[F[_]: Concurrent](client: Client[F],
      baseUri: Uri = Docker.versionPrefix): F[Boolean] = 
      client.successful(Request[F](Method.HEAD, baseUri / "_ping"))
  }
}
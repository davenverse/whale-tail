package io.chrisdavenport.whaletail

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import io.circe._
import org.http4s.circe._

object System {
  object Operations {
    def info[F[_]: Concurrent](client: Client[F]): F[Json] = 
      client.expect[Json](Request[F](Method.GET, Docker.versionPrefix / "info"))

    def version[F[_]: Concurrent](client: Client[F]): F[Json] = 
      client.expect[Json](Request[F](Method.GET, Docker.versionPrefix / "version"))

    def ping[F[_]: Concurrent](client: Client[F]): F[Boolean] = 
      client.successful(Request[F](Method.HEAD, Docker.versionPrefix / "_ping"))
  }
}
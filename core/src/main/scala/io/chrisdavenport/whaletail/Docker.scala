package io.chrisdavenport.whaletail

import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.headers.Host
import org.http4s.client.Client
import org.http4s.implicits._
// import jnr.unixsocket.UnixSocketAddress
import scala.concurrent.duration._

// import org.http4s.ember.backdoor.EmberBackdoor // Caution: Package Private Means Bincompat Cannot Be guaranteed
import scala.concurrent.duration.FiniteDuration
import fs2.Chunk.ByteVectorChunk
import scodec.bits.ByteVector

import org.http4s.ember.client.EmberClientBuilder
import fs2.io.net.unixsocket.UnixSocketAddress
import org.http4s.client.middleware.UnixSocket


object Docker {

  val versionPrefix: Uri = uri"http://v1.41"

  def client[F[_]: Async]: Resource[F, Client[F]] = {
    EmberClientBuilder
      .default[F]
      .build
      .map(UnixSocket(UnixSocketAddress("/var/run/docker.sock"))(_))
      .map(withHost(_))
  }

  private def withHost[F[_]](req: Request[F]): Request[F] = 
    req.headers.get[Host].as(req).getOrElse(req.putHeaders(Host("whale-tail")))
  private def withHost[F[_]: Concurrent](client: Client[F]): Client[F] = 
    Client(req => client.run(withHost(req)))
}
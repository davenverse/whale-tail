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

  val versionPrefix: Uri = uri"v1.41"

  val defaultUnixSocketAddress = UnixSocketAddress("/var/run/docker.sock")
  val defaultTLSAddress = uri"tcp://0.0.0.0:2375"

  def unixSocket[F[_]: Async](socketAddress: UnixSocketAddress): Resource[F, Client[F]] = {
    EmberClientBuilder
      .default[F]
      .build
      .map(UnixSocket(socketAddress)(_))
      .map(withHost(_))
  }

  def tls[F[_]: Async](baseUri: Uri): Resource[F, Client[F]] = {
    EmberClientBuilder
      .default[F]
      .build
      .map(hostPortOverride[F](baseUri)(MonadCancelThrow[F])(_))
      .map(withHost(_))
  }

  def default[F[_]: Async]: Resource[F, Client[F]] = for {
    baseUriSOpt <- Resource.eval(Sync[F].delay(sys.env.get("DOCKER_HOST")))
    base <- Resource.eval(baseUriSOpt.traverse(parseConnection[F]))
    out <- base match {
      case Some(Left(baseUri)) => tls(baseUri)
      case Some(Right(socket)) => unixSocket(socket)
      case None => unixSocket(defaultUnixSocketAddress)
    }
  } yield out

  private def withHost[F[_]](req: Request[F]): Request[F] = 
    req.headers.get[Host].as(req).getOrElse(req.putHeaders(Host("whale-tail")))
  private def withHost[F[_]: Concurrent](client: Client[F]): Client[F] = 
    Client(req => client.run(withHost(req)))
  private def hostPortOverride[F[_]: MonadCancelThrow](baseUri: Uri) = { (client: Client[F]) =>
    Client[F] { (req: Request[F]) =>
      val uri = baseUri.resolve(req.uri)
      val newReq = req.withUri(uri)
      client.run(newReq)
    }
  }

  private val UNIX_SOCKET = "unix://(.*)".r
  private def parseConnection[F[_]: Async](s: String): F[Either[Uri, UnixSocketAddress]] = s match {
    case UNIX_SOCKET(file) =>  UnixSocketAddress(file).asRight.pure[F]
    case other => Uri.fromString(other).liftTo[F].map(_.asLeft)
  }
}
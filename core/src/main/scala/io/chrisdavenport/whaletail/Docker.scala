package io.chrisdavenport.whaletail

import cats.implicits._
import cats.effect._
import org.http4s.Request
import org.http4s.headers.Host
import org.http4s.client.Client
import io.chrisdavenport.log4cats.Logger
import jnr.unixsocket.UnixSocketAddress
import fs2.io.tcp.Socket
import scala.concurrent.duration._

import org.http4s.ember.backdoor.EmberBackdoor // Caution: Package Private Means Bincompat Cannot Be guaranteed
import scala.concurrent.duration.FiniteDuration


object Docker {
  
  def default[F[_]: Concurrent: ContextShift: Timer](blocker: Blocker, logger: Logger[F]): Client[F] = {
    Client{ req =>
      UnixSocket.client[F](dockerSocketAddr, blocker).flatMap{ socket =>
        fromSocket(socket, logger).run(req)
      }
    }
  }

  def fromSocket[F[_]: Sync](
    socket: Socket[F],
    logger: Logger[F],
    maxHeadersLength: Int = 4096,
    maxResponseBytesRead: Int = 32 * 1024,
    requestIdleTimeout: Option[FiniteDuration] = 60.seconds.some,
    responseIdleTimeout: Option[FiniteDuration] = 60.seconds.some
  ): Client[F] = Client( req => 
      Resource.liftF(
        EmberBackdoor.requestEncoder(withHost(req)) // Docker Requires a Host Header
          .through(socket.writes(requestIdleTimeout))
          .compile
          .drain
      ) >> EmberBackdoor.responseParser(maxHeadersLength, logger)(socket.reads(maxResponseBytesRead, responseIdleTimeout))
  )

  private val dockerSocketAddr = new UnixSocketAddress("/var/run/docker.sock")
  private def withHost[F[_]](req: Request[F]): Request[F] = 
    req.headers.get(Host).as(req).getOrElse(req.withHeaders(Host("whale-tail")))
}
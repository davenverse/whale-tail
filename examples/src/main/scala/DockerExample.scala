import cats.syntax.all._
import cats.effect._
import cats.effect.concurrent._
import io.circe._
import fs2._
import scala.concurrent.duration._
import fs2.Chunk.ByteBuffer
import scodec.bits.ByteVector
import fs2.Chunk.ByteVectorChunk
import java.nio.charset.StandardCharsets
import jnr.unixsocket.UnixSocketAddress
import java.nio.file.Paths
import _root_.io.chrisdavenport.log4cats.Logger
import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import fs2.io.tls.TLSContext

import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.circe._
import org.http4s.headers.Host

import _root_.io.chrisdavenport.whaletail.UnixSocket
import _root_.io.chrisdavenport.whaletail.Docker
import _root_.org.http4s.ember.backdoor.EmberBackdoor

object DockerExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = Slf4jLogger.getLogger[IO]
    for {
      blocker <- Blocker[IO]
      client = Docker.default(blocker, logger)

      _ <- Resource.liftF(
        client.expect[Json](Request[IO](Method.GET, uri"/info"))
          .flatTap(a => logger.info(a.toString()))
      )
      _ <- Resource.liftF(
        client.expect[Json](Request[IO](Method.GET, uri"/version"))
          .flatTap(a => logger.info(a.toString()))
      )
      // _ <- Resource.liftF(
      //   client.expect[String](Request[IO](Method.GET, uri"/ping"))
      //     .flatTap(a => logger.info(a.toString()))
      // )

      // _ <- Resource.liftF(
      //   client.expect[Json](Request[IO](Method.GET, uri"/containers/json")).flatTap(a => logger.info(a.toString()))
      // )

      // _ <- Resource.liftF(
      //   client.expect[Json](Request[IO](Method.GET, uri"/images/json")).flatTap(a => logger.info(a.toString()))
      // )

    } yield ()
    
  }.use(_ => IO(ExitCode.Success))

}
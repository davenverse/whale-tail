import cats.implicits._
import cats.effect._
import io.circe._
import fs2._
import scala.concurrent.duration._
import fs2.Chunk.ByteBuffer
import scodec.bits.ByteVector
import fs2.Chunk.ByteVectorChunk
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import _root_.org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.circe._
import org.http4s.headers.Host

import _root_.io.chrisdavenport.whaletail.Docker

object DockerExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = Slf4jLogger.getLogger[IO]
    for {
      client <- Docker.client[IO]

      _ <- Resource.eval(
        client.expect[Json](Request[IO](Method.GET, uri"/info"))
          .flatTap(a => logger.info(a.toString()))
      )
      _ <- Resource.eval(
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
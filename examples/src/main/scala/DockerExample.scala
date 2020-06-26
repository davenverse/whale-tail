import cats.implicits._
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

import _root_.io.chrisdavenport.whaletail.UnixSocket
import _root_.org.http4s.ember.backdoor.EmberBackdoor

object DockerExample extends IOApp {
  // val unix = "unix://"
  val dockerSocket = "/var/run/docker.sock"
  // val localSocket = "/tmp/aSocket.sock"

  def run(args: List[String]): IO[ExitCode] = {
    val logger = Slf4jLogger.getLogger[IO]
    for {
      blocker <- Blocker[IO]
      address = new UnixSocketAddress(dockerSocket)
      socket <- UnixSocket.client[IO](address, blocker)
      _ <- Resource.liftF(IO(println("Client Connected")))

      _ <- Resource.liftF(
        socket.localAddress.flatTap(a => IO(println(a))) >>
        socket.remoteAddress.flatTap(a => IO(println(a))) >>
        socket.isOpen.flatTap(a => IO(println(a)))
      )

      // context <- Resource.liftF(TLSContext.system[IO](blocker))
      client <- Resource.liftF(createClient(socket, logger))
      val req = Request[IO](Method.GET, uri"/info")
          .withHeaders(org.http4s.headers.Host("cool"))
      // _ <- Resource.liftF(
      //   client.run(req).use(resp => 
      //     logger.info(s"Response: $resp") >> 
      //     resp.bodyAsText.compile.string.flatMap{
      //       body => logger.info(s"Body: ..$body..")
      //     }
      //   )
      // )
      // _ <- Resource.liftF(logger.info(s"Got: $json"))

      _ <- Resource.liftF(
        EmberBackdoor.requestEncoder[IO](req)
        .through(socket.writes(10.seconds.some))
        .compile
        .drain
      )
      _ <- Resource.liftF(IO(println("Wrote Data Succesfully")))
      _ <- Resource.liftF(
        socket.reads( 32 * 1024, 10.seconds.some)
          .debugChunks{ch => ch.toByteVector.toString()}
          .through(fs2.text.utf8Decode)
          .through(fs2.text.lines)
          .evalMap(s => IO(println(s)))
          .compile
          .drain
      )

    } yield ()
    
  }.use(_ => IO(ExitCode.Success))

  private def createClient[F[_]: Concurrent](socket: fs2.io.tcp.Socket[F], logger: Logger[F]): F[Client[F]] = {
    Semaphore[F](1).map{ sem => 
      Client{
        req: Request[F] => 

        Resource.make(sem.acquire)(_ => sem.release) >> 
        Resource.liftF(
          EmberBackdoor.requestEncoder(req)
            .through(socket.writes(None))
            .compile
            .drain
        ) >> 
        EmberBackdoor.responseParser(4096, logger)(socket.reads( 32 * 1024))
      }

    }

  }

}
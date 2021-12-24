import cats.implicits._
import cats.effect._
import fs2._
import fs2.io.net.unixsocket.{UnixSockets, UnixSocketAddress}
import scala.concurrent.duration._
import fs2.Chunk.ByteBuffer
import scodec.bits.ByteVector
import fs2.Chunk.ByteVectorChunk
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpApp
import org.http4s.ember.client.EmberClient
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.middleware.UnixSocket
  import org.http4s._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.headers.Connection

object StaticLocationExample extends IOApp {
  // val unix = "unix://"
  // val dockerSocket = "/var/run/docker.sock"
  val localSocket = UnixSocketAddress("/tmp/aSocket.sock")
  val logger = Slf4jLogger.getLogger[IO]

  def run(args: List[String]): IO[ExitCode] = {
    for {
      server <- EmberServerBuilder
        .default[IO]
        .withUnixSocketConfig(UnixSockets[IO], localSocket)
        .withHttpApp(app)
        .withLogger(logger)
        .withErrorHandler({case e => logger.info(e)("Information").as(Response[IO](Status.InternalServerError))})
        .build
      _ <- Resource.eval(IO(println("Server Connected")))

      client <- EmberClientBuilder.default[IO].build.map(UnixSocket(localSocket))
      _ <- Resource.eval(IO.sleep(1.second))
      resp <- client.run(Request[IO]())

      _ <- Resource.eval(IO(println(resp)))
    } yield ()
    
  }.use(_ => IO(ExitCode.Success))


  def app = HttpApp.liftF(Response[IO](Status.Ok).withEntity("Hello Unix Sockets!").pure[IO])

}
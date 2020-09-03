import cats.syntax.all._
import cats.effect._
import fs2._
import scala.concurrent.duration._
import fs2.Chunk.ByteBuffer
import scodec.bits.ByteVector
import fs2.Chunk.ByteVectorChunk
import java.nio.charset.StandardCharsets
import jnr.unixsocket.UnixSocketAddress
import java.nio.file.Paths

import _root_.io.chrisdavenport.whaletail.UnixSocket

object StaticLocationExample extends IOApp {
  // val unix = "unix://"
  // val dockerSocket = "/var/run/docker.sock"
  val localSocket = "/tmp/aSocket.sock"

  def run(args: List[String]): IO[ExitCode] = {
    for {
      blocker <- Blocker[IO]
      address = new UnixSocketAddress(localSocket)
      _ <- Resource.liftF(fs2.io.file.deleteIfExists[IO](blocker, Paths.get(localSocket)))
      server <- UnixSocket.server[IO](address, blocker)
      _ <- Resource.liftF(IO(println("Server Connected")))
      client <- UnixSocket.client[IO](address, blocker)
      _ <- Resource.liftF(IO(println("Client Connected")))

      _ <- Resource.liftF(
        client.localAddress.flatTap(a => IO(println(a))) >>
        client.remoteAddress.flatTap(a => IO(println(a))) >>
        client.isOpen.flatTap(a => IO(println(a))) >>

        server.map(r => 
          Stream.resource(r)
            .flatMap(_.reads(512))
            .chunks
            .flatMap{c => if (c.size > 1) Stream.emit(c) else Stream.empty}
            .flatMap(Stream.chunk(_))
            .through(fs2.text.utf8Decode)
            .evalTap(i => IO(println(i)))
        )
          .concurrently(
            Stream.awakeDelay[IO](1.second).as{
              "Hello"
              }
              .through(fs2.text.utf8Encode)
              .through(client.writes(None))
          ).parJoin(5).timeout(10.seconds)
            .compile
            .drain
      )
    } yield ()
    
  }.use(_ => IO(ExitCode.Success))

}
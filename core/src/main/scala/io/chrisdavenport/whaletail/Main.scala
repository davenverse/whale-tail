package io.chrisdavenport.whaletail

import cats.implicits._
import cats.effect._
import fs2._
import scala.concurrent.duration._
import fs2.Chunk.ByteBuffer
import scodec.bits.ByteVector
import fs2.Chunk.ByteVectorChunk
import java.nio.charset.StandardCharsets

object Main extends IOApp {
  val unix = "unix://"
  val dockerSocket = "/var/run/docker.sock"
  val localSocket = "./aSocket.sock"
  val req = "GET /info"

  def run(args: List[String]): IO[ExitCode] = {
    for {
      blocker <- Blocker[IO]
      socket <- UnixSocket.impl[IO](localSocket, blocker)

      _ <- Resource.liftF(
        socket.localAddress.flatTap(a => IO(println(a))) >>
        socket.remoteAddress.flatTap(a => IO(println(a))) >>
        socket.isOpen.flatTap(a => IO(println(a))) >>
        socket.readN(10).flatTap(a => IO(println(a)))

        // out.reads(512).chunks.evalTap(a => IO(a.toByteVector.decodeUtf8))
        //   .concurrently(
        //     Stream.eval(
        //       out.write(ByteVectorChunk(ByteVector.encodeString("Hello")(StandardCharsets.UTF_8).fold(throw _, identity)))
        //     )
        //   ).timeout(10.seconds)
        //   .compile
        //   .drain
        
      )
      // socket <- UnixSocket.impl[IO](dockerSocket, blocker)
      // _ <- Resource.liftF(
      //   Stream(req).through(fs2.text.utf8Encode).evalTap(c => IO(println(s"Wrote: $c"))).through(socket.write).compile.drain
      // )
      // _ <- Resource.liftF(IO(println("wrote to socket")))
      // _ <- Resource.liftF(
      //   socket.read
      //   .through(fs2.text.utf8Decode)
      //   .evalMap(s => IO(println(s"Read: $s")))
      //   .compile.drain
      // )

        // .concurrently(
        //   Stream(req).through(fs2.text.utf8Encode).evalTap(c => IO(println(s"Wrote: $c"))).through(socket.write)
        // ).timeout(20.seconds).compile.resource.drain
          
    } yield ()
    
  }.use(_ => IO(ExitCode.Success))

}

// def unixSocketChannel(path: String)(implicit cs: ContextShift[IO]): fs2.Stream[IO, Channel] =
//   for {
//     blocker <- Stream.resource(Blocker[IO])
//     channel <- Stream.resource(unixSocket(path))
//   } yield {
//     val getOutputStream = IO(Channels.newOutputStream(channel))
//     val getInputStream  = IO(Channels.newInputStream(channel))
//     val writePipe       =
//       fs2.io.writeOutputStream(getOutputStream, blocker, closeAfterUse = false)
//     val readStream      = fs2.io
//       .readInputStream(getInputStream, 4194304, blocker, closeAfterUse = false)
//     (writePipe -> readStream)
//   }
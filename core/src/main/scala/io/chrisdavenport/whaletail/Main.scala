package io.chrisdavenport.whaletail

import cats.effect._
import fs2._
import scala.concurrent.duration._
import fs2.Chunk.ByteBuffer

object Main extends IOApp {
  val unix = "unix://"
  val dockerSocket = "/var/run/docker.sock"
  val req = "GET /info"

  def run(args: List[String]): IO[ExitCode] = {
    for {
      blocker <- Blocker[IO]
      socket <- UnixSocket.impl[IO](dockerSocket, blocker)
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


import cats.effect._
import fs2._
import fs2.io.tcp.Socket
import java.nio.channels.Channels
import jnr.unixsocket.UnixSocketChannel
import jnr.unixsocket.UnixSocketAddress
import java.nio.{Buffer, ByteBuffer => JByteBuffer}

// trait UnixSocket[F[_]]{
//   def read: Stream[F, Byte]
//   def write: Pipe[F, Byte, Unit]
// }
object UnixSocket {
  def unixSocket[F[_]: Sync](path: String) = {
    Resource.make(
      Sync[F].delay(UnixSocketChannel.open(new UnixSocketAddress(path)))
    )(channel => Sync[F].delay(channel.close()))
  } 
  def impl[F[_]: ConcurrentEffect: ContextShift](socketPath: String, blocker: Blocker): Resource[F, Socket[F]] = {
    unixSocket(socketPath).map{ channel => // UnixSocketChannel <: AbstractNativeSocketChannel extends SocketChannel
       //implements ByteChannel, NativeSelectableChannel


      // val getOutputStream = Sync[F].delay(Channels.newOutputStream(channel))
      // val getInputStream  = Sync[F].delay(Channels.newInputStream(channel))
      // val writePipe       =
      //   fs2.io.writeOutputStream(getOutputStream, blocker, closeAfterUse = false)
      // val readStream      = fs2.io
      //   .readInputStream(getInputStream, 4194304, blocker, closeAfterUse = false)

      // Socket is TCP encrypted

      new Socket[F] {
  def close: F[Unit] = Sync[F].delay(channel.close())
  def endOfInput: F[Unit] = Sync[F].delay(channel.shutdownInput())
  def endOfOutput: F[Unit] = Sync[F].delay(channel.shutdownOutput())
  def isOpen: F[Boolean] = Sync[F].delay(channel.isOpen())
  def localAddress: F[java.net.SocketAddress] = Sync[F].delay(channel.getLocalAddress())
  def read(maxBytes: Int, timeout: Option[scala.concurrent.duration.FiniteDuration]): F[Option[fs2.Chunk[Byte]]] = ???
    // Sync[F].delay(channel.read(JByteBuffer.allocate(maxBytes)))
    
  def readN(numBytes: Int, timeout: Option[scala.concurrent.duration.FiniteDuration]): F[Option[fs2.Chunk[Byte]]] = ???
  def reads(maxBytes: Int, timeout: Option[scala.concurrent.duration.FiniteDuration]): fs2.Stream[F,Byte] = ???
  def remoteAddress: F[java.net.SocketAddress] = ???
  def write(bytes: fs2.Chunk[Byte], timeout: Option[scala.concurrent.duration.FiniteDuration]): F[Unit] = ???
    
  def writes(timeout: Option[scala.concurrent.duration.FiniteDuration]): fs2.Pipe[F,Byte,Unit] = ???
        // def read: Stream[F, Byte] = readStream
        // def write: Pipe[F, Byte, Unit] = writePipe
      }

    }
  }


}

// def main(socketPath: String) = {
//   implicit val cs = IO.contextShift(ExecutionContext.global)

//   unixSocketChannel(socketPath).flatMap(_._2.take(5).map(println)).compile.drain.unsafeRunSync()
// }

// type WritePipe  = Pipe[IO, Byte, Unit]
// type ReadStream = Stream[IO, Byte]
// type Channel    = (WritePipe, ReadStream)



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
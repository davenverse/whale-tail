package io.chrisdavenport.whaletail

import cats.implicits._
import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import fs2._
import fs2.io.tcp.Socket
import java.nio.channels.Channels
import jnr.unixsocket._ // This is where the fun begins
import jnr.unixsocket.impl.AbstractNativeSocketChannel
import java.nio.{Buffer, ByteBuffer}
import java.net.SocketAddress
import scala.concurrent.duration._

object UnixSocket {

  def serverResource[F[_]](
      address: UnixSocketAddress,
      // reuseAddress: Boolean = true,
      // receiveBufferSize: Int = 256 * 1024,
      // additionalSocketOptions: List[SocketOptionMapping[_]] = List.empty,
      blocker: Blocker
  )(implicit
      F: Concurrent[F],
      CS: ContextShift[F],
      Timer: Timer[F]
  ): Resource[F, Stream[F, Resource[F, Socket[F]]]] = {
    def setup = blocker.delay{
      val serverChannel = UnixServerSocketChannel.open()
      serverChannel.configureBlocking(false)
      serverChannel.socket().bind(address)
      serverChannel
    }

    def cleanup(sch: UnixServerSocketChannel): F[Unit] = {
        blocker.delay{
          if (sch.isOpen) println("Server Socket Was Open, Closing"); sch.close()
          if (sch.isRegistered()) println("Server Still Registered")
        }
    }

    def acceptIncoming(sch: UnixServerSocketChannel): Stream[F, Resource[F, Socket[F]]] = {
      def go: Stream[F, Resource[F, Socket[F]]] = {
        def acceptChannel: F[UnixSocketChannel] =
          blocker.delay{
            val ch = sch.accept()
            ch.configureBlocking(false)
            ch
          }
        //   asyncYield[F, AsynchronousSocketChannel] { cb =>
        //     sch.accept(
        //       null,
        //       new CompletionHandler[AsynchronousSocketChannel, Void] {
        //         def completed(ch: AsynchronousSocketChannel, attachment: Void): Unit =
        //           cb(Right(ch))
        //         def failed(rsn: Throwable, attachment: Void): Unit =
        //           cb(Left(rsn))
        //       }
        //     )
        //   }

        Stream.eval(acceptChannel.attempt).flatMap {
          case Left(_)         => Stream.empty[F]
          case Right(accepted) => Stream.emit(makeSocket(accepted, blocker))
        } ++ go
      }
    

      go
    }

    Resource.make(setup)(cleanup).map { sch =>
      acceptIncoming(sch)
    }
  }

  def makeSocket[F[_]](
      ch: AbstractNativeSocketChannel,
      blocker: Blocker
  )(implicit F: Concurrent[F], cs: ContextShift[F], timer: Timer[F]): Resource[F, Socket[F]] = {
    val socket = (Semaphore[F](1), Semaphore[F](1), Ref[F].of(ByteBuffer.allocate(0))).mapN {
          (readSemaphore, writeSemaphore, bufferRef) =>
        // Reads data to remaining capacity of supplied ByteBuffer
        // Also measures time the read took returning this as tuple
        // of (bytes_read, read_duration)

        def now: F[Long] = Sync[F].delay(System.currentTimeMillis())

        def readChunk(buff: ByteBuffer, timeoutMs: Long): F[(Int, Long)] =
          (now, blocker.delay(ch.read(buff)), now).mapN{
            case (before, out, now) => (out, now - before)
          }//.timeout(timeoutMs.millis) 

        // gets buffer of desired capacity, ready for the first read operation
        // If the buffer does not have desired capacity it is resized (recreated)
        // buffer is also reset to be ready to be written into.
        def getBufferOf(sz: Int): F[ByteBuffer] =
          bufferRef.get.flatMap { buff =>
            if (buff.capacity() < sz)
              F.delay(ByteBuffer.allocate(sz)).flatTap(bufferRef.set)
            else
              F.delay {
                (buff: Buffer).clear()
                (buff: Buffer).limit(sz)
                buff
              }
          }

        // When the read operation is done, this will read up to buffer's position bytes from the buffer
        // this expects the buffer's position to be at bytes read + 1
        def releaseBuffer(buff: ByteBuffer): F[Chunk[Byte]] =
          F.delay {
            val read = buff.position()
            val result =
              if (read == 0) Chunk.bytes(Array.empty)
              else {
                val dest = new Array[Byte](read)
                (buff: Buffer).flip()
                buff.get(dest)
                Chunk.bytes(dest)
              }
            (buff: Buffer).clear()
            result
          }

        def read0(max: Int, timeout: Option[FiniteDuration]): F[Option[Chunk[Byte]]] =
          readSemaphore.withPermit {
            getBufferOf(max).flatMap { buff =>
              readChunk(buff, timeout.map(_.toMillis).getOrElse(0L)).flatMap {
                case (read, _) =>
                  if (read < 0) F.pure(None)
                  else releaseBuffer(buff).map(Some(_))
              }
            }
          }

        def readN0(max: Int, timeout: Option[FiniteDuration]): F[Option[Chunk[Byte]]] =
          readSemaphore.withPermit {
            getBufferOf(max).flatMap { buff =>
              def go(timeoutMs: Long): F[Option[Chunk[Byte]]] =
                readChunk(buff, timeoutMs).flatMap {
                  case (readBytes, took) =>
                    if (readBytes < 0 || buff.position() >= max)
                      // read is done
                      releaseBuffer(buff).map(Some(_))
                    else go((timeoutMs - took).max(0))
                }

              go(timeout.map(_.toMillis).getOrElse(0L))
            }
          }

        def write0(bytes: Chunk[Byte], timeout: Option[FiniteDuration]): F[Unit] = {
          def go(buff: ByteBuffer, remains: Long): F[Unit] = {
              val base = for {
                before <- now
                _ <- blocker.delay(ch.write(buff))
                after <- now
              } yield {
                if (buff.remaining() <= 0) None
                else Some(after - before)
              }
              base
              //timeout.map(base.timeout(_)).getOrElse(base)
            }.flatMap {
              case None       => F.pure(())
              case Some(took) => go(buff, (remains - took).max(0))
            }
          writeSemaphore.withPermit {
            go(bytes.toBytes.toByteBuffer, timeout.map(_.toMillis).getOrElse(0L))
          }
        }

        ////////////

      new Socket[F]{
          def readN(numBytes: Int, timeout: Option[FiniteDuration]): F[Option[Chunk[Byte]]] =
            readN0(numBytes, timeout)
          def read(maxBytes: Int, timeout: Option[FiniteDuration]): F[Option[Chunk[Byte]]] =
            read0(maxBytes, timeout)
          def reads(maxBytes: Int, timeout: Option[FiniteDuration]): Stream[F, Byte] =
            Stream.eval(read(maxBytes, timeout)).flatMap {
              case Some(bytes) =>
                Stream.chunk(bytes) ++ reads(maxBytes, timeout)
              case None => Stream.empty
            }

          def write(bytes: Chunk[Byte], timeout: Option[FiniteDuration]): F[Unit] =
            write0(bytes, timeout)
          def writes(timeout: Option[FiniteDuration]): Pipe[F, Byte, Unit] =
            _.chunks.flatMap(bs => Stream.eval(write(bs, timeout)))

          def localAddress: F[SocketAddress] =
            blocker.delay(ch.getLocalAddress)
          def remoteAddress: F[SocketAddress] =
            blocker.delay(ch.getRemoteAddress)
          def isOpen: F[Boolean] = blocker.delay(ch.isOpen)
          def close: F[Unit] = blocker.delay(ch.close())
          def endOfOutput: F[Unit] =
            blocker.delay {
              ch.shutdownOutput(); ()
            }
          def endOfInput: F[Unit] =
            blocker.delay {
              ch.shutdownInput(); ()
            }
      }
    }
    Resource.make(socket)(_ => blocker.delay(if (ch.isOpen) ch.close else ()).attempt.void)
  }

  def client[F[_]: Concurrent: ContextShift: Timer](path: String, blocker: Blocker): Resource[F, Socket[F]] = 
    Resource.liftF(Sync[F].delay(UnixSocketChannel.open(new UnixSocketAddress(path))))
      .flatMap(makeSocket[F](_, blocker))


}
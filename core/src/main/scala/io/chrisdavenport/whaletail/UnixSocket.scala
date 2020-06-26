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
import java.nio.file._

object UnixSocket {

  def client[F[_]: Concurrent: ContextShift: Timer](address: UnixSocketAddress, blocker: Blocker): Resource[F, Socket[F]] = 
    Resource.liftF(Sync[F].delay(UnixSocketChannel.open(address)))
      .flatMap(makeSocket[F](_, blocker))


  def server[F[_]](
      address: UnixSocketAddress,
      blocker: Blocker
  )(implicit
      F: Concurrent[F],
      CS: ContextShift[F],
      Timer: Timer[F]
  ): Resource[F, Stream[F, Resource[F, Socket[F]]]] = {
    def setup = fs2.io.file.exists(blocker, Paths.get(address.path)).ifM(
      F.raiseError(throw new Throwable("Socket Location Already Exists, Server cannot create Socket Location when location already exists."))
      ,
      blocker.delay{
        val serverChannel = UnixServerSocketChannel.open()
        serverChannel.configureBlocking(false)
        val sock = serverChannel.socket()
        sock.bind(address)
        serverChannel
      }
    )

    def cleanup(sch: UnixServerSocketChannel): F[Unit] = {
        blocker.delay{
          if (sch.isOpen) sch.close()
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

  private def makeSocket[F[_]](
      ch: AbstractNativeSocketChannel,
      blocker: Blocker
  )(implicit F: Concurrent[F], cs: ContextShift[F], timer: Timer[F]): Resource[F, Socket[F]] = {
    val socket = (Semaphore[F](1), Semaphore[F](1), Ref[F].of(ByteBuffer.allocate(0))).mapN {
          (readSemaphore, writeSemaphore, bufferRef) =>
        // Reads data to remaining capacity of supplied ByteBuffer
        def readChunk(buff: ByteBuffer): F[Int] =
          blocker.delay(ch.read(buff))

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

        def read0(max: Int, timeout: Option[FiniteDuration]): F[Option[Chunk[Byte]]] = {
          val action = readSemaphore.withPermit {
            getBufferOf(max).flatMap { buff =>
              readChunk(buff).flatMap {
                case read =>
                  if (read < 0) F.pure(Option.empty[Chunk[Byte]])
                  else releaseBuffer(buff).map(_.some)
              }
            }
          }
          timeout.fold(action)(action.timeout(_))
        }

        def readN0(max: Int, timeout: Option[FiniteDuration]): F[Option[Chunk[Byte]]] = {
          val action = readSemaphore.withPermit {
            getBufferOf(max).flatMap { buff =>
              def internalAction : F[Option[Chunk[Byte]]] =
                readChunk(buff).flatMap {
                  case  readBytes =>
                    if (readBytes < 0 || buff.position() >= max)
                      // read is done
                      releaseBuffer(buff).map(_.some)
                    else internalAction
                }

              internalAction
            }
          }
          timeout.fold(action)(action.timeout(_))
        }

        def write0(bytes: Chunk[Byte], timeout: Option[FiniteDuration]): F[Unit] = {
          def go(buff: ByteBuffer): F[Unit] = {
            blocker.delay(ch.write(buff)) >> {
              if (buff.remaining <= 0) F.unit
              else go(buff)
            }
          }
          val action = writeSemaphore.withPermit {
            go(bytes.toBytes.toByteBuffer)
          }

          timeout.fold(action)(time => action.timeout(time))
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



}
package org.http4s.ember.backdoor

import cats.effect._
import org.http4s._
import io.chrisdavenport.log4cats.Logger
import fs2.Stream
import org.http4s.ember.core.Parser
import org.http4s.ember.core.Encoder

// Note The Odd Package: Necessary as we are leverage Ember package private interfaces.
object EmberBackdoor {

  def requestEncoder[F[_]: Sync](req: Request[F]): Stream[F, Byte] = Encoder.reqToBytes(req)

  def responseParser[F[_]: Sync](maxHeaderLength: Int, logger: Logger[F])(s: Stream[F, Byte]): Resource[F, Response[F]] = 
    Parser.Response.parser[F](maxHeaderLength)(s)(logger)
  
}


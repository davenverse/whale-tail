package io.chrisdavenport.whaletail.manager

import cats.syntax.all._
import io.chrisdavenport.whaletail._
import scala.concurrent.duration._
import cats.effect._
import scala.util.matching.Regex
import scala.concurrent.duration._
import org.http4s._

sealed trait ReadinessStrategy
object ReadinessStrategy {
  // Waits this long before declaring a container healthy
  case class Delay(duration: FiniteDuration) extends ReadinessStrategy
  // Matches On This Regex from the Containers Logs
  case class LogRegex(regex: Regex, times: Int = 1) extends ReadinessStrategy

  import cats._
  import org.http4s.client.Client
  import cats.effect.syntax.all._
  def checkReadiness[F[_]: Temporal](
    client: Client[F],
    setup: WhaleTailContainer,
    strategy: ReadinessStrategy,
    timeout: Duration,
    baseUri: Uri = Docker.versionPrefix
  ): F[Unit] = {
    val action = strategy match {
      case Delay(duration) => Temporal[F].sleep(duration)
      case LogRegex(regex, times) => 
        Containers.Operations.logs(client, setup.id, baseUri = baseUri).flatMap{ s => 
          if (regex.findAllIn(s).size >= times) Applicative[F].unit
          else Temporal[F].sleep(10.millis) >> checkReadiness(client, setup, strategy, Duration.Inf, baseUri)
        }
    }
    timeout match {
      case t: FiniteDuration => Temporal[F].timeout(action, t)
      case _ => action
    }
  }
}
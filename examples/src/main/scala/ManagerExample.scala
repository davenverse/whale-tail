
import cats.implicits._
import cats.effect._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.whaletail.{
  Docker,
  Containers,
  Images
}
import scala.concurrent.duration._
import io.chrisdavenport.whaletail.manager._

object ManagerExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    for {
      client <- Docker.default[IO].map(org.http4s.client.middleware.Logger(true, false))
      container <- WhaleTailContainer.build(client, "redis", "latest".some, Map(6379 -> None), Map.empty, Map.empty)
        .evalTap(
          ReadinessStrategy.checkReadiness(
            client,
            _, 
            ReadinessStrategy.LogRegex(".*Ready to accept connections.*\\s".r),
            30.seconds
          )
        )
    } yield ()
    

  }.use(_ => IO.pure(ExitCode.Success))
} 
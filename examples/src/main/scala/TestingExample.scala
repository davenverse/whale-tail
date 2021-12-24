
import cats.implicits._
import cats.effect._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.whaletail.{
  Docker,
  Containers,
  Images
}
import scala.concurrent.duration._
import io.chrisdavenport.whaletailtesting._

object ATestApp extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = Slf4jLogger.getLogger[IO]
    implicit class LogAll[A](fa: IO[A]){
      def logInfo(tag: String) = fa.flatTap(a => logger.info(tag ++ ": " ++ a.toString()))
    } 
    for {
      client <- Docker.client[IO]
      setup <- WhaleTailTestContainer.build(client, "redis", "latest".some, Map(6379 -> None), Map.empty, Map.empty)
      _ <- Resource.eval(
        logger.info(s"$setup")
      )
      _ <- Resource.eval(
        ReadinessStrategy.checkReadiness(
          client,
          setup, 
          ReadinessStrategy.LogRegex(".*Ready to accept connections.*\\s".r),
          30.seconds
        )
      )
    } yield ()
    

  }.use(_ => IO.pure(ExitCode.Success))
} 
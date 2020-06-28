
import cats.implicits._
import cats.effect._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.whaletail.{
  Docker,
  Containers
}

object ContainersExample extends IOApp {

  

  def run(args: List[String]): IO[ExitCode] = {
    val logger = Slf4jLogger.getLogger[IO]
    implicit class LogAll[A](fa: IO[A]){
      def logInfo = fa.flatTap(a => logger.info(a.toString()))
    } 
    for {
      blocker <- Blocker[IO]
      client = Docker.default(blocker, logger)
      created <- Resource.liftF(
        Containers.Operations.create(client, "redis:latest").logInfo
      )
    } yield ()
  }.use(_ => IO.pure(ExitCode.Success))

}
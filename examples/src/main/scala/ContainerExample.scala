
import cats.implicits._
import cats.effect._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.whaletail.{
  Docker,
  Containers,
  Images
}
import scala.concurrent.duration._

object ContainersExample extends IOApp {

  

  def run(args: List[String]): IO[ExitCode] = {
    val logger = Slf4jLogger.getLogger[IO]
    implicit class LogAll[A](fa: IO[A]){
      def logInfo = fa.flatTap(a => logger.info(a.toString()))
    } 
    for {
      blocker <- Blocker[IO]
      client = Docker.default(blocker, logger)
      _ <- Resource.liftF(
        Images.Operations.createFromImage(client, "redis", "latest".some).logInfo
      )
      created <- Resource.liftF(
        Containers.Operations.create(client, "redis:latest", Map(6379 -> 6379)).logInfo
      )
      _ <- Resource.make(
        Containers.Operations.start(client, created.id).logInfo
      )(_ => 
        Containers.Operations.stop(client, created.id, None).logInfo.void
      )
      _ <- Resource.liftF(
        Containers.Operations.inspect(client, created.id).logInfo
      )

      _ <- Resource.liftF(
        Timer[IO].sleep(2.seconds) >> Containers.Operations.logs(client, created.id).logInfo
    
      )
      _ <- Resource.liftF(Timer[IO].sleep(5.minutes))
    } yield ()
    

  }.use(_ => IO.pure(ExitCode.Success))

}

import cats.implicits._
import cats.effect._
import org.typelevel.log4cats.slf4j.Slf4jLogger
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
      def logInfo(tag: String) = fa.flatTap(a => logger.info(tag ++ ": " ++ a.toString()))
    } 
    for {

      client <- Docker.client[IO]
      _ <- Resource.eval(
        Images.Operations.createFromImage(client, "redis", "latest".some).logInfo("createFromImage")
      )
      created <- Resource.eval(
        Containers.Operations.create(client, "redis:latest", Map(6379 -> 6379)).logInfo("create")
      )
      _ <- Resource.make(
        Containers.Operations.start(client, created.id).logInfo("start")
      )(_ => 
        Containers.Operations.stop(client, created.id, None).logInfo("stop").void
      )
      _ <- Resource.eval(
        Containers.Operations.inspect(client, created.id).logInfo("inspect")
      )

      _ <- Resource.eval(
        IO.sleep(2.seconds) >> Containers.Operations.logs(client, created.id).logInfo("logs")
    
      )
      _ <- Resource.eval(IO.sleep(30.minutes))
    } yield ()
    

  }.use(_ => IO.pure(ExitCode.Success))

}
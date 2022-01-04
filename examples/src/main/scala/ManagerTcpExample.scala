
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.client._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.whaletail.{
  Docker,
  Containers,
  Images
}
import scala.concurrent.duration._
import io.chrisdavenport.whaletail.manager._
import org.http4s.ember.client.EmberClientBuilder
import com.comcast.ip4s._
import org.http4s.implicits._


object ManagerTcpExample extends IOApp {
  
  // If you are not running a tls version of docker you can get one via this command
  // docker run -d -v /var/run/docker.sock:/var/run/docker.sock -p 127.0.0.1:1234:1234 bobrik/socat TCP-LISTEN:1234,fork UNIX-CONNECT:/var/run/docker.sock

  // You can use it explicitly like below or use
  // Docker.default

  val resource = Docker.tls[IO](uri"tcp://localhost:1234")
    .flatMap(client =>
      WhaleTailContainer
        .build(client, "redis", "latest".some, Map(6379 -> None), Map.empty, Map.empty)
        .evalTap(
          ReadinessStrategy.checkReadiness(
            client,
            _,
            ReadinessStrategy.LogRegex(".*Ready to accept connections.*\\s".r),
            30.seconds
          )
        ))
    .flatMap(container =>
      for {
        t             <- Resource.eval(
                           container.ports.get(6379).liftTo[IO](new Throwable("Missing Port"))
                         )
        (hostS, portI) = t
        host          <- Resource.eval(Host.fromString(hostS).liftTo[IO](new Throwable("Invalid Host")))
        port          <- Resource.eval(Port.fromInt(portI).liftTo[IO](new Throwable("Invalid Port")))
        // connection    <- RedisConnection.pool[IO].withHost(host).withPort(port).build
        _             <- Resource.eval(IO.println("Connection Created."))
      } yield (host, port)
    )
    def run(args: List[String]): IO[ExitCode] = resource.use( a => 
      IO.println(s"${a._1}, ${a._2}")
    ).as(ExitCode.Success)
}
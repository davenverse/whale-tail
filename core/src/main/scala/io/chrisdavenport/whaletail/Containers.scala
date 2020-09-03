package io.chrisdavenport.whaletail

import cats._
import cats.syntax.all._
import cats.effect._

import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client._
import org.http4s.circe._
import scala.concurrent.duration.FiniteDuration
import cats.data.NonEmptyMapOps

trait Containers[F[_]]{
  def create(image: String, name: Option[String]): F[Json]
}

object Containers {

  object Operations {

    private val containersPrefix = Docker.versionPrefix / "containers"

    def create[F[_]: Sync](
      client: Client[F],
      image: String,
      exposedPorts: Map[Int, Int] = Map.empty, // Container Port, Host Port
      env: Map[String, String] = Map.empty
    ) = 
      client.run(
        Request[F](Method.POST, containersPrefix / "create")
          .withEntity{
            Json.obj(
              "Image" -> image.asJson,
              "ExposedPorts" -> Json.obj(
                exposedPorts.toList.map{ case (i, _) => s"$i/tcp" -> Json.obj()}:_*
              ),
              "Env" -> Alternative[Option].guard(env.size > 0).as(
                env.toList.map{case (key, value) => s"$key=$value"}.asJson
              ).asJson,
              "HostConfig" -> Json.obj(
                "PortBindings" -> Json.obj(
                  exposedPorts.toList.map{ case (container, host) => s"$container/tcp" -> Json.arr(
                    Json.obj(
                      "HostPort" -> s"$host".asJson
                    )
                  )}:_*
                )
              )
            ).dropNullValues
          }
      ).use{resp => 
        if (resp.status === Status.Created)
          JsonDecoder[F].asJsonDecode[Data.ContainerCreated](resp) 
        else 
          resp.bodyAsText.compile.string.flatMap{body => 
            ApplicativeError[F, Throwable].raiseError[Data.ContainerCreated](
              Data.ContainersErrorResponse(resp.status, resp.headers, resp.httpVersion, body)
            )
          }
      }

    def inspect[F[_]: JsonDecoder: Bracket[*[_], Throwable]](
      client: Client[F],
      id: String
    ) = client.run(
      Request[F](Method.GET, containersPrefix / id / "json")
    ).use(resp => 
      JsonDecoder[F].asJson(resp)
    )

    def start[F[_]: Bracket[*[_], Throwable]](
      client: Client[F],
      id: String
    ): F[Status] = client.status(Request[F](Method.POST, containersPrefix / id / "start"))

    def stop[F[_]: Bracket[*[_], Throwable]](
      client: Client[F],
      id: String,
      waitBeforeKilling: Option[FiniteDuration] = None
    ): F[Status] = client.status(
      Request[F](
        Method.POST, 
        (containersPrefix / id / "stop")
          .setQueryParams(Map("t" -> waitBeforeKilling.map(_.toSeconds).toSeq))
      )
    )

    def logs[F[_]: Sync](
      client: Client[F],
      id: String
    ): F[String] = client.run(
      Request[F](
        Method.GET, 
        (containersPrefix / id / "logs")
          .setQueryParams(Map(
            "follow" -> Seq(false),
            "stdout" -> Seq(true),
            "stderr" -> Seq(true),
          ))
      )
    )
      .use(_.bodyText.compile.string)

  }

  object Data {
    final case class ContainerCreated(id: String, warnings: List[String])
    object ContainerCreated{
      implicit val decoder: Decoder[ContainerCreated] =  new Decoder[ContainerCreated]{
        def apply(c: HCursor): Decoder.Result[ContainerCreated] = 
          (
            c.downField("Id").as[String],
            c.downField("Warnings").as[List[String]]
          ).mapN(ContainerCreated.apply)
      }
    }

    final case class ContainersErrorResponse(status: Status, headers: Headers, httpVersion: HttpVersion, body: String) 
      extends Throwable(show"Containers Response Not Expected - Status: $status, headers: $headers, httpVersion: $httpVersion, body:$body")
  }
}
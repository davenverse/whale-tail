package io.chrisdavenport.whaletail

import cats._
import cats.implicits._
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

    def create[F[_]: JsonDecoder: Bracket[*[_], Throwable]](
      client: Client[F],
      image: String,
      exposedPorts: Set[Int] = Set.empty,
      env: Map[String, String] = Map.empty
    ) = 
      client.run(
        Request[F](Method.POST, containersPrefix / "create")
          .withEntity(Json.obj(
            "Image" -> image.asJson,
            "ExposedPorts" -> Json.obj(
              exposedPorts.toList.map(i => s"$i/tcp" -> Json.obj()):_*
            ),
            "Env" -> Alternative[Option].guard(env.size > 0).as(
              List(
                env.toList.map{case (key, value) => s"$key=$value"}
              ).asJson
            ).asJson
          ).dropNullValues
          )
      ).use{resp => 
        JsonDecoder[F].asJsonDecode[Data.ContainerCreated](resp)
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
  }
}
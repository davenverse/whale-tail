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

trait Containers[F[_]]{
  def create(image: String, name: Option[String]): F[Json]
}

object Containers {

  object Operations {

    private val containersPrefix = Docker.versionPrefix / "containers"

    def create[F[_]: JsonDecoder: Bracket[*[_], Throwable]](
      client: Client[F],
      image: String
    ) = 
      client.run(
        Request[F](Method.POST, containersPrefix / "create")
          .withEntity(Json.obj(
            "Image" -> image.asJson
          ))
      ).use{resp => 
      JsonDecoder[F].asJsonDecode[Data.ContainerCreated](resp)
      }

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
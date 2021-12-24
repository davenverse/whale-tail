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

    def list[F[_]: Concurrent](
      client: Client[F],
      all: Boolean = false,
      limit: Option[Int] = None,
      size: Boolean = false,
      filters: Map[String, List[String]] = Map.empty
    ) = {
      val base = containersPrefix / "json"
      val uri = base.withQueryParam("all", all.toString())
        .withOptionQueryParam("limit", limit.map(_.toString))
        .withQueryParam("size", size.toString())
        .withQueryParam("filters", filters.asJson.printWith(Printer.noSpaces))
      val req = Request[F](Method.GET, uri)

      client.run(req).use(resp => 
        if (resp.status === Status.Ok)
          JsonDecoder[F].asJsonDecode[Json](resp) 
        else 
          resp.bodyText.compile.string.flatMap{body => 
            ApplicativeError[F, Throwable].raiseError[Json](
              Data.ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body)
            )
          }
      )
    }

    def create[F[_]: Concurrent](
      client: Client[F],
      image: String,
      exposedPorts: Map[Int, Option[Int]] = Map.empty, // Container Port, Host Port (None binds random)
      env: Map[String, String] = Map.empty
    ) = {
      val req = Request[F](Method.POST, containersPrefix / "create")
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
                      "HostPort" -> s"${host.getOrElse("")}".asJson
                    )
                  )}:_*
                )
              )
            ).dropNullValues
          }
      client.run(req).use{resp => 
        if (resp.status === Status.Created)
          JsonDecoder[F].asJsonDecode[Data.ContainerCreated](resp) 
        else 
          resp.bodyText.compile.string.flatMap{body => 
            ApplicativeError[F, Throwable].raiseError[Data.ContainerCreated](
              Data.ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body)
            )
          }
      }
    }

    def inspect[F[_]: JsonDecoder: Concurrent](
      client: Client[F],
      id: String
    ) = {
      val req = Request[F](Method.GET, containersPrefix / id / "json")
      client.run(req).use(resp => 
        if (resp.status === Status.Ok) JsonDecoder[F].asJson(resp)
        else resp.bodyText.compile.string.flatMap(body => 
          ApplicativeError[F, Throwable].raiseError[Json](
            Data.ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body)
          )
        )
      )
    }


    def start[F[_]: Concurrent](
      client: Client[F],
      id: String
    ): F[Boolean]= {
      val req = Request[F](Method.POST, 
          containersPrefix / id / "start",
          headers = Headers(org.http4s.headers.`Content-Length`(0)) // This is here, because without it this call fails
        )
      client.run(req).use(
        resp => 
          if (resp.status === Status.NoContent) true.pure[F]
          else if (resp.status === Status.NotModified) false.pure[F]
          else resp.bodyText.compile.string.flatMap(body => 
            ApplicativeError[F, Throwable].raiseError(
              Data.ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body)
            )
          )
      )
  }

    def stop[F[_]: Concurrent](
      client: Client[F],
      id: String,
      waitBeforeKilling: Option[FiniteDuration] = None
    ): F[Boolean] = {
      val req = Request[F](
          Method.POST, 
          (containersPrefix / id / "stop")
            .setQueryParams(Map("t" -> waitBeforeKilling.map(_.toSeconds).toSeq))
        )
      client.run(req).use(resp => 
          if (resp.status === Status.NoContent) true.pure[F]
          else if (resp.status === Status.NotModified) false.pure[F]
          else resp.bodyText.compile.string.flatMap(body => 
            ApplicativeError[F, Throwable].raiseError(
              Data.ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body)
            )
          )
        )
    }

    def logs[F[_]: Concurrent](
      client: Client[F],
      id: String,
      stdout: Boolean = true,
      stderr: Boolean = false,
    ): F[String] = {
      val req = Request[F](
          Method.GET, 
          (containersPrefix / id / "logs")
            .setQueryParams(Map(
              "follow" -> Seq(false),
              "stdout" -> Seq(stdout),
              "stderr" -> Seq(stderr),
            ))
        )
      client.run(req).use{resp => 
        if (resp.status === Status.Ok) resp.bodyText.compile.string
        else resp.bodyText.compile.string.flatMap(body => 
          ApplicativeError[F, Throwable].raiseError(
            Data.ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body)
          )
        )
      }
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

    final case class ContainersErrorResponse(req: RequestPrelude, resp: ResponsePrelude, body: String) 
      extends Throwable(show"Containers Response Not Expected for Request: $req -  Status: ${resp.status}, headers: ${resp.headers}, httpVersion: ${resp.httpVersion}, body:$body")
  }
}
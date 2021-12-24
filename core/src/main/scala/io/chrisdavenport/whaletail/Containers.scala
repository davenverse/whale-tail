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

object Containers {

  object Operations {

    private val containersPrefix = (uri: Uri) => uri / "containers"

    def list[F[_]: Concurrent](
      client: Client[F],
      all: Boolean = false,
      limit: Option[Int] = None,
      size: Boolean = false,
      filters: Map[String, List[String]] = Map.empty,
      baseUri: Uri = Docker.versionPrefix
    ): F[Json] = {
      val base = containersPrefix(baseUri) / "json"
      val uri = base.withQueryParam("all", all.toString())
        .withOptionQueryParam("limit", limit.map(_.toString))
        .withQueryParam("size", size.toString())
        .withQueryParam("filters", filters.asJson.printWith(Printer.noSpaces))
      val req = Request[F](Method.GET, uri)

      client.run(req).use(resp => 
        if (resp.status === Status.Ok)
          JsonDecoder[F].asJsonDecode[Json](resp) 
        else 
          Data.ContainersErrorResponse.raise(req, resp)
      )
    }

    def create[F[_]: Concurrent](
      client: Client[F],
      image: String,
      exposedPorts: Map[Int, Option[Int]] = Map.empty, // Container Port, Host Port (None binds random)
      env: Map[String, String] = Map.empty,
      baseUri: Uri = Docker.versionPrefix
    ): F[Data.ContainerCreated] = {
      val req = Request[F](Method.POST, containersPrefix(baseUri) / "create")
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
          Data.ContainersErrorResponse.raise(req, resp)
      }
    }

    def inspect[F[_]: JsonDecoder: Concurrent](
      client: Client[F],
      id: String,
      baseUri: Uri = Docker.versionPrefix
    ): F[Json] = {
      val req = Request[F](Method.GET, containersPrefix(baseUri) / id / "json")
      client.run(req).use(resp => 
        if (resp.status === Status.Ok) JsonDecoder[F].asJson(resp)
        else Data.ContainersErrorResponse.raise(req, resp)
      )
    }


    def start[F[_]: Concurrent](
      client: Client[F],
      id: String,
      baseUri: Uri = Docker.versionPrefix
    ): F[Boolean]= {
      val req = Request[F](
        Method.POST, 
        containersPrefix(baseUri) / id / "start",
        headers = Headers(org.http4s.headers.`Content-Length`(0)) // This is here, because without it this call fails
      )
      client.run(req).use(resp => 
        if (resp.status === Status.NoContent) true.pure[F]
        else if (resp.status === Status.NotModified) false.pure[F]
        else Data.ContainersErrorResponse.raise(req, resp)
      )
  }

    def stop[F[_]: Concurrent](
      client: Client[F],
      id: String,
      waitBeforeKilling: Option[FiniteDuration] = None,
      baseUri: Uri = Docker.versionPrefix
    ): F[Boolean] = {
      val req = Request[F](
          Method.POST, 
          (containersPrefix(baseUri) / id / "stop")
            .setQueryParams(Map("t" -> waitBeforeKilling.map(_.toSeconds).toSeq))
        )
      client.run(req).use(resp => 
        if (resp.status === Status.NoContent) true.pure[F]
        else if (resp.status === Status.NotModified) false.pure[F]
        else Data.ContainersErrorResponse.raise(req, resp)
      )
    }

    def logs[F[_]: Concurrent](
      client: Client[F],
      id: String,
      stdout: Boolean = true,
      stderr: Boolean = false,
      baseUri: Uri = Docker.versionPrefix
    ): F[String] = {
      val req = Request[F](
          Method.GET, 
          (containersPrefix(baseUri) / id / "logs")
            .setQueryParams(Map(
              "follow" -> Seq(false),
              "stdout" -> Seq(stdout),
              "stderr" -> Seq(stderr),
            ))
        )
      client.run(req).use{resp => 
        if (resp.status === Status.Ok) resp.bodyText.compile.string
        else Data.ContainersErrorResponse.raise(req, resp)
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
      extends RuntimeException(show"Containers Response Not Expected for Request: $req -  Status: ${resp.status}, headers: ${resp.headers}, httpVersion: ${resp.httpVersion}, body:$body")
    object ContainersErrorResponse {
      def raise[F[_]: Concurrent, A](req: Request[F], resp: Response[F]): F[A] = 
        resp.bodyText.compile.string.flatMap{ body  => 
          ApplicativeThrow[F].raiseError(ContainersErrorResponse(req.requestPrelude, resp.responsePrelude, body))
        }
    }
  }
}
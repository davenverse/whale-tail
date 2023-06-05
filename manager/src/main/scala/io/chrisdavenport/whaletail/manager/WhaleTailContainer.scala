package io.chrisdavenport.whaletail.manager

import io.chrisdavenport.whaletail._
import cats.effect._
import cats.syntax.all._
import io.circe._
import cats.data._
import org.http4s.Uri

case class WhaleTailContainer(name: String, ports: Map[Int, (String, Int)], id: String)
object WhaleTailContainer {
  import org.http4s.client.Client
  def build[F[_]: Concurrent](
    client: Client[F],
    image: String,
    tag: Option[String],
    ports: Map[Int, Option[Int]],
    env: Map[String, String],
    labels: Map[String, String],
    baseUri: Uri = Docker.versionPrefix,
    binds: List[Containers.Bind] = Nil
  ): Resource[F,  WhaleTailContainer] = {
    for {
      img <- Resource.eval(
        Images.Operations.createFromImage(client, image, tag, baseUri = baseUri)
      )
      created <- Resource.eval(
        Containers.Operations.create(client, s"$image${tag.map(s => s":$s").getOrElse("")}", ports, env, labels = Map("whale-identity" -> "whale-tail") ++ labels, baseUri = baseUri, binds = binds)
      )
      _ <- Resource.make(
        Containers.Operations.start(client, created.id, baseUri = baseUri)
      )(_ => 
        Containers.Operations.stop(client, created.id, None, baseUri = baseUri).void
      )
      json <- Resource.eval(
        Containers.Operations.inspect(client, created.id, baseUri = baseUri)
      )
      setup <- Resource.eval(
        json.as[WhaleTailContainer](decoder(ports.keys.toList)).liftTo[F]
      )
    } yield setup
  }

  private case class PortCombo(host: String, port: Int)
  private object PortCombo {
    implicit val decoder: Decoder[PortCombo] = new Decoder[PortCombo]{
      def apply(c: HCursor): Decoder.Result[PortCombo] = for {
        host <- c.downField("HostIp").as[String]
        t = c.downField("HostPort")
        portS <- t.as[String]
        port <- Either.catchNonFatal(portS.toInt).leftMap(e => io.circe.DecodingFailure(e.getMessage(), t.history))
      } yield PortCombo(host, port)
    }
  }
  import io.circe._
  def decoder(ports: List[Int]): Decoder[WhaleTailContainer] = new Decoder[WhaleTailContainer]{
    def apply(c: HCursor): Decoder.Result[WhaleTailContainer] = for {
      id <- c.downField("Id").as[String]
      name <- c.downField("Name").as[String]
      portsJson = c.downField("NetworkSettings").downField("Ports")
      out <- ports.traverse(i => 
        portsJson.downField(s"$i/tcp").as[NonEmptyList[PortCombo]].map(a => a.head.host -> a.head.port).tupleLeft(i)
      )
    } yield {
      WhaleTailContainer(name, out.toMap, id)
    }
  }
}

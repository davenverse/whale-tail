package io.chrisdavenport.whaletailtesting

import io.chrisdavenport.whaletail._
import cats.effect._
import cats.syntax.all._
import io.circe._
import cats.data._

case class WhaleTailTestContainer(name: String, ports: Map[Int, (String, Int)], id: String)
object WhaleTailTestContainer {
  import org.http4s.client.Client
  def build[F[_]: Concurrent](
    client: Client[F],
    image: String,
    tag: Option[String],
    ports: Map[Int, Option[Int]],
    env: Map[String, String],
    labels: Map[String, String]
  ): Resource[F,  WhaleTailTestContainer] = {
    for {
      img <- Resource.eval(
        Images.Operations.createFromImage(client, image, tag)
      )
      created <- Resource.eval(
        Containers.Operations.create(client, s"$image${tag.map(s => s":$s").getOrElse("")}", ports, labels = Map("whale-identity" -> "whale-tail") ++ labels)
      )
      _ <- Resource.make(
        Containers.Operations.start(client, created.id)
      )(_ => 
        Containers.Operations.stop(client, created.id, None).void
      )
      json <- Resource.eval(
        Containers.Operations.inspect(client, created.id)
      )
      setup <- Resource.eval(
        json.as[WhaleTailTestContainer](decoder(ports.keys.toList)).liftTo[F]
      )
    } yield setup
  }

  private case class PortCombo(host: String, port: Int)
  private object PortCombo {
    implicit val decoder = new Decoder[PortCombo]{
      def apply(c: HCursor): Decoder.Result[PortCombo] = for {
        host <- c.downField("HostIp").as[String]
        t = c.downField("HostPort")
        portS <- t.as[String]
        port <- Either.catchNonFatal(portS.toInt).leftMap(e => io.circe.DecodingFailure(e.getMessage(), t.history))
      } yield PortCombo(host, port)
    }
  }
  import io.circe._
  def decoder(ports: List[Int]) = new Decoder[WhaleTailTestContainer]{
    def apply(c: HCursor): Decoder.Result[WhaleTailTestContainer] = for {
      id <- c.downField("Id").as[String]
      name <- c.downField("Name").as[String]
      portsJson = c.downField("NetworkSettings").downField("Ports")
      out <- ports.traverse(i => 
        portsJson.downField(s"$i/tcp").as[NonEmptyList[PortCombo]].map(a => a.head.host -> a.head.port).tupleLeft(i)
      )
    } yield {
      WhaleTailTestContainer(name, out.toMap, id)
    }
  }
}
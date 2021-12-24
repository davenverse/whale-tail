package io.chrisdavenport.whaletail

import org.specs2._
import cats.effect._
import cats.effect.testing.specs2.CatsEffect

object SystemSpec extends mutable.Specification with CatsEffect  {

  "System" should {
    "be able to get info" in {
      Docker.client[IO].use(c => 
        System.Operations.info(c).attempt
          .map(e => e must beRight)
      )
    }

    "be able to get version" in {
      Docker.client[IO].use(c => 
        System.Operations.version(c).attempt
          .map(e => e must beRight)
      )
    }

    "be able to ping" in {
      Docker.client[IO].use(c => 
        System.Operations.ping(c).attempt
          .map(e => e must beRight)
      )
    }
  }

}
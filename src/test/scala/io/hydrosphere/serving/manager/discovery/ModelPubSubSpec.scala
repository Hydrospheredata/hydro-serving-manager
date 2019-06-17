package io.hydrosphere.serving.manager.discovery

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.GraphDSL
import cats.effect.IO
import cats.implicits._
import cats.effect.implicits._
import fs2.{Pipe, Sink}
import fs2.concurrent.{SignallingRef, Topic}
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.model.{ModelDelete, ModelDiscoveryPublisher, ModelEvent, ModelUpdate}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}

import scala.concurrent.ExecutionContext

class ModelPubSubSpec extends GenericUnitTest {
  implicit val cs = IO.contextShift(ExecutionContext.global)
  val mv = ModelVersion(
    id = 1,
    image = DockerImage("asd", "asd"),
    created = LocalDateTime.now(),
    finished = None,
    modelVersion = 2,
    modelContract = ModelContract.defaultInstance,
    runtime = DockerImage("asd", "asd"),
    model = Model(1, "asd"),
    hostSelector = None,
    status = ModelVersionStatus.Released,
    profileTypes = Map.empty,
    installCommand = None,
    metadata = Map.empty
  )
  describe("Model PubSub") {
    it("should distribute event among all subs") {
      val topic = Topic[IO, ModelEvent](ModelUpdate(Nil)).unsafeRunSync()
      val (pub, sub) = ModelDiscoveryPublisher.forTopic[IO](topic)
      val s1 = sub.subscribe("test1").unsafeRunSync().evalMap { mv =>
        IO(println(mv)).as(mv)
      }
      pub.update(mv).unsafeRunSync()
      pub.update(mv.copy(id = 2)).unsafeRunSync()
      pub.update(mv.copy(id = 3)).unsafeRunSync()
      pub.update(mv.copy(id = 4)).unsafeRunSync()

      val res = s1.take(1).compile.toVector.unsafeRunSync()
      assert(res.nonEmpty)
    }

  }
}

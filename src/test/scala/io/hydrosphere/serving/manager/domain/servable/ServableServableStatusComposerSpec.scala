package io.hydrosphere.serving.manager.domain.servable

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}

import cats.implicits._

import java.time.Instant

class ServableServableStatusComposerSpec extends GenericUnitTest {
  val mv: ModelVersion.Internal = ModelVersion.Internal(
    id = 10,
    image = DockerImage("name", "tag"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelSignature = Signature.defaultSignature,
    runtime = DockerImage("runtime", "tag"),
    model = Model(1, "name"),
    status = ModelVersionStatus.Released,
    installCommand = None,
    metadata = Map.empty
  )

  val servingServable: Servable = Servable(
    modelVersion = mv,
    name = "servable",
    status = Servable.Status.Serving,
    message = None,
    host = None,
    port = None,
    usedApps = Nil,
    metadata = Map.empty,
    deploymentConfiguration = None
  )

  val notServingServable: Servable = Servable(
    modelVersion = mv,
    name = "servable",
    status = Servable.Status.NotServing,
    message = "Servable failed".some,
    host = None,
    port = None,
    usedApps = Nil,
    metadata = Map.empty,
    deploymentConfiguration = None
  )

  describe("with one servable") {
    it("returns status of that servable") {
      val servable                   = servingServable
      val list: List[Servable]       = List(servingServable)
      val (messages, resultedStatus) = ServableStatusComposer.combineStatuses(list);

      assert(messages.isEmpty)
      assert(resultedStatus == servable.status)
    }
  }

  describe("with one NotServing servable") {
    it("returns status of that servable") {
      val list: List[Servable]       = List(notServingServable, servingServable)
      val (messages, resultedStatus) = ServableStatusComposer.combineStatuses(list)

      assert(messages.headOption == notServingServable.message)
      assert(resultedStatus == Servable.Status.NotServing)
    }
  }

  describe("with many NotServing servable") {
    it("returns status of that servable") {
      val list: List[Servable]       = List(notServingServable, notServingServable)
      val (messages, resultedStatus) = ServableStatusComposer.combineStatuses(list)

      assert(messages.length == 2)
      messages.foreach(message => assert(message == notServingServable.message.get))
      assert(resultedStatus == Servable.Status.NotServing)
    }
  }
}

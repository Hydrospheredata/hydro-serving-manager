package io.hydrosphere.serving.manager.it.service

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.domain.application.requests.{
  CreateApplicationRequest,
  ExecutionGraphRequest,
  ModelVariantRequest,
  PipelineStageRequest
}
import io.hydrosphere.serving.manager.domain.contract.DataType.DT_DOUBLE
import io.hydrosphere.serving.manager.domain.contract.{Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.collection.JavaConverters._

class DockerAppSpec extends FullIntegrationSpec with BeforeAndAfterAll {
  private val uploadFile = packModel("/models/dummy_model")
  private val signature = Signature(
    signatureName = "not-default-spark",
    inputs = NonEmptyList.of(
      Field.Tensor(
        "test-input",
        DT_DOUBLE,
        TensorShape.scalar,
        none
      )
    ),
    outputs = NonEmptyList.of(
      Field.Tensor(
        "test-output",
        DT_DOUBLE,
        TensorShape.scalar,
        none
      )
    )
  )
  private val upload1 = ModelUploadMetadata(
    name = "m1",
    runtime = dummyImage,
    modelSignature = signature.some
  )
  private val upload2 = ModelUploadMetadata(
    name = "m2",
    runtime = dummyImage,
    modelSignature = signature.some
  )

  var mv1: ModelVersion.Internal = _
  var mv2: ModelVersion.Internal = _

  describe("Application and Servable service") {
    it("should delete unused servables after deletion") {
      ioAssert {
        val create = CreateApplicationRequest(
          "simple-app",
          None,
          ExecutionGraphRequest(
            NonEmptyList.of(
              PipelineStageRequest(
                NonEmptyList.of(
                  ModelVariantRequest(
                    modelVersionId = mv1.id,
                    weight = 100
                  )
                )
              )
            )
          ),
          None,
          None
        )
        for {
          appResult <- app.core.appService.create(create)
          _ = println("Sent creation request")
          _ = println("Creation completed")
          preCont <- IO(dockerClient.listContainers())
          _ = println("sleep")
          _ <- timer.sleep(5.seconds)
          _ <- app.core.appService.delete(appResult.name)
          _ = println("deleted app")
          cont <- IO(dockerClient.listContainers())
        } yield {
          println("App containers:")
          preCont.forEach(println)
          println("---end of containers---")
          println("After containers:")
          cont.forEach(println)
          println("---end of containers---")
          assert(preCont.asScala !== cont.asScala)
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    dockerClient.pull("hydrosphere/serving-runtime-dummy:latest")

    val f = for {
      d1         <- app.core.modelService.uploadModel(uploadFile, upload1)
      completed1 <- d1.completed.get
      d2         <- app.core.modelService.uploadModel(uploadFile, upload2)
      completed2 <- d2.completed.get
    } yield {
      println(s"UPLOADED: $completed1")
      println(s"UPLOADED: $completed2")
      mv1 = completed1
      mv2 = completed2
    }
    f.unsafeRunSync()
  }
}

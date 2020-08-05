package io.hydrosphere.serving.manager.it.service

import cats.data.NonEmptyList
import cats.effect.IO
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.application.requests.{CreateApplicationRequest, ExecutionGraphRequest, ModelVariantRequest, PipelineStageRequest}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.tensorflow.types.DataType.DT_DOUBLE
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.collection.JavaConverters._

class DockerAppSpec extends FullIntegrationSpec with BeforeAndAfterAll {
  private val uploadFile = packModel("/models/dummy_model")
  private val signature = ModelSignature(
    signatureName = "not-default-spark",
    inputs = List(ModelField("test-input", None, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DT_DOUBLE))),
    outputs = List(ModelField("test-output", None, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DT_DOUBLE)))
  )
  private val upload1 = ModelUploadMetadata(
    name = "m1",
    runtime = dummyImage,
    contract = Some(ModelContract(
      predict = Some(signature)
    )),
    monitoringConfiguration = Some(MonitoringConfiguration())
  )
  private val upload2 = ModelUploadMetadata(
    name = "m2",
    runtime = dummyImage,
    contract = Some(ModelContract(
      predict = Some(signature)
    )),
    monitoringConfiguration = Some(MonitoringConfiguration())
  )

  var mv1: ModelVersion.Internal = _
  var mv2: ModelVersion.Internal = _

//  describe("Application and Servable service") {
//    it("should delete unused servables after deletion") {
//      ioAssert {
//        val create = CreateApplicationRequest(
//          "simple-app",
//          None,
//          ExecutionGraphRequest(NonEmptyList.of(
//            PipelineStageRequest(
//              NonEmptyList.of(ModelVariantRequest(
//                modelVersionId = mv1.id,
//                weight = 100
//              ))
//            ))
//          ),
//          None,
//          None
//        )
//        for {
//          appResult <- app.core.appService.create(create)
//          _ = println("Sent creation request")
//          _ <- appResult.completed.get
//          _ = println("Creation completed")
//          preCont <- IO(dockerClient.listContainers())
//          _ = println("sleep")
//          _ <- timer.sleep(5.seconds)
//          _ <- app.core.appService.delete(appResult.started.name)
//          _ = println("deleted app")
//          cont <- IO(dockerClient.listContainers())
//        } yield {
//          println("App containers:")
//          preCont.forEach(println)
//          println("---end of containers---")
//          println("After containers:")
//          cont.forEach(println)
//          println("---end of containers---")
//          assert(preCont.asScala !== cont.asScala)
//        }
//      }
//    }
//  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    dockerClient.pull("hydrosphere/serving-runtime-dummy:latest")

    val f = for {
      d1 <- app.core.modelService.uploadModel(uploadFile, upload1)
      completed1 <- d1.completed.get
      d2 <- app.core.modelService.uploadModel(uploadFile, upload2)
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
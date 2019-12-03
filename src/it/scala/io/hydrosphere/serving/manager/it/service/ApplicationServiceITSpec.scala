package io.hydrosphere.serving.manager.it.service

import cats.data.{NonEmptyList, OptionT}
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.tensorflow.types.DataType.DT_DOUBLE
import org.scalatest.BeforeAndAfterAll

class ApplicationServiceITSpec extends FullIntegrationSpec with BeforeAndAfterAll {
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
    ))
  )
  private val upload2 = ModelUploadMetadata(
    name = "m2",
    runtime = dummyImage,
    contract = Some(ModelContract(
      predict = Some(signature)
    ))
  )
  private val upload3 = ModelUploadMetadata(
    name = "m3",
    runtime = dummyImage,
    contract = Some(ModelContract(
      predict = Some(signature)
    ))
  )

  var mv1: ModelVersion.Internal = _
  var mv2: ModelVersion.Internal = _
  var mv3: ModelVersion.Internal = _

  describe("Application service") {
    it("should create a simple application") {
      ioAssert {
        val create = CreateApplicationRequest(
          "simple-app",
          None,
          ExecutionGraphRequest(NonEmptyList.of(
            PipelineStageRequest(
              NonEmptyList.of(ModelVariantRequest(
                modelVersionId = mv1.id,
                weight = 100
              ))
            ))
          ),
          None,
          None
        )
        for {
          appResult <- app.core.appService.create(create)
          started = appResult.started
          finished <- appResult.completed.get
          servables <- app.core.repos.servableRepo.all()
        } yield {
          assert(started.name === "simple-app")
          assert(finished.status.isInstanceOf[Application.Ready], finished.status)
          assert(started.signature.inputs === mv1.modelContract.predict.get.inputs)
          assert(started.signature.outputs === mv1.modelContract.predict.get.outputs)
          val status = finished.status.asInstanceOf[Application.Ready]
          val models = status.stages.flatMap(_.variants)
          assert(models.head.weight === 100)
          assert(models.head.item.modelVersion.id === mv1.id)
          logger.debug(s"Servables: $servables")
          assert(servables.nonEmpty)
        }
      }
    }

    it("should create a multi-service stage") {
      ioAssert {
        val appRequest = CreateApplicationRequest(
          name = "MultiServiceStage",
          namespace = None,
          executionGraph = ExecutionGraphRequest(
            stages = NonEmptyList.of(
              PipelineStageRequest(
                modelVariants = NonEmptyList.of(
                  ModelVariantRequest(
                    modelVersionId = mv1.id,
                    weight = 60
                  ),
                  ModelVariantRequest(
                    modelVersionId = mv1.id,
                    weight = 40
                  )
                )
              )
            )
          ),
          kafkaStreaming = None,
          metadata = None
        )
        for {
          app <- app.core.appService.create(appRequest)
          finished <- app.completed.get
        } yield {
          println(app)
          assert(app.started.name === appRequest.name)
          val status = finished.status.asInstanceOf[Application.Ready]
          val services = status.stages.flatMap(_.variants)
          val service1 = services.head
          val service2 = services.tail.head
          assert(service1.weight === 60)
          assert(service1.item.modelVersion.id === mv1.id)
          assert(service2.weight === 40)
          assert(service2.item.modelVersion.id === mv1.id)
        }
      }
    }

    it("should create and update an application with kafkaStreaming") {
      val appRequest = CreateApplicationRequest(
        name = "kafka_app",
        namespace = None,
        executionGraph = ExecutionGraphRequest(
          stages = NonEmptyList.of(
            PipelineStageRequest(
              modelVariants = NonEmptyList.of(
                ModelVariantRequest(
                  modelVersionId = mv1.id,
                  weight = 100
                )
              )
            )
          )
        ),
        kafkaStreaming = Some(List(
          ApplicationKafkaStream(
            sourceTopic = "source",
            destinationTopic = "dest",
            consumerId = None,
            errorTopic = None
          )
        )),
        metadata = None
      )
      ioAssert {
        for {
          application <- app.core.appService.create(appRequest)
          _ <- application.completed.get
          appNew <- app.core.appService.update(UpdateApplicationRequest(
            application.started.id,
            application.started.name,
            application.started.namespace,
            appRequest.executionGraph,
            None,
            None
          ))
          finishedNew <- appNew.completed.get
          gotNewApp <- OptionT(app.core.repos.appRepo.get(appNew.started.id))
            .getOrElse(throw new IllegalArgumentException("no applicaiton"))
        } yield {
          assert(finishedNew === gotNewApp)
          assert(appNew.started.kafkaStreaming.isEmpty, appNew)
        }
      }
    }

    it("should create and update an application contract") {
      ioAssert {
        val appRequest = CreateApplicationRequest(
          name = "contract_app",
          namespace = None,
          executionGraph = ExecutionGraphRequest(
            stages = NonEmptyList.of(
              PipelineStageRequest(
                modelVariants = NonEmptyList.of(
                  ModelVariantRequest(
                    modelVersionId = mv1.id,
                    weight = 100
                  )
                )
              )
            )
          ),
          kafkaStreaming = None,
          metadata = None
        )
        for {
          application <- app.core.appService.create(appRequest)
          newGraph = ExecutionGraphRequest(
            stages = NonEmptyList.of(
              PipelineStageRequest(
                modelVariants = NonEmptyList.of(
                  ModelVariantRequest(
                    modelVersionId = mv2.id,
                    weight = 100
                  )
                )
              )
            )
          )
          appNew <- app.core.appService.update(UpdateApplicationRequest(
            application.started.id,
            application.started.name,
            application.started.namespace,
            newGraph,
            None,
            None
          ))

          gotNewApp <- OptionT(app.core.repos.appRepo.get(appNew.started.id))
            .getOrElse(throw DomainError.notFound("app not found"))
        } yield {
          assert(appNew.started === gotNewApp, gotNewApp)
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    dockerClient.pull("hydrosphere/serving-runtime-dummy:latest")

    val f = for {
      d1 <- app.core.modelService.uploadModel(uploadFile, upload1)
      completed1 <- d1.completed.get
      d2 <- app.core.modelService.uploadModel(uploadFile, upload2)
      completed2 <- d2.completed.get
      d3 <- app.core.modelService.uploadModel(uploadFile, upload3)
      completed3 <- d3.completed.get
    } yield {
      println(s"UPLOADED: $completed1")
      println(s"UPLOADED: $completed2")
      println(s"UPLOADED: $completed3")
      mv1 = completed1
      mv2 = completed2
      mv3 = completed3
    }
    f.unsafeRunSync()
  }
}
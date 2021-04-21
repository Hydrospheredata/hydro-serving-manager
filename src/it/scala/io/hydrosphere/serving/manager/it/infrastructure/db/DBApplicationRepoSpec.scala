package io.hydrosphere.serving.manager.it.infrastructure.db

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.option._
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.domain.application.{
  Application,
  ApplicationGraph,
  ApplicationServable,
  ApplicationStage
}
import io.hydrosphere.serving.manager.domain.contract.DataType.DT_DOUBLE
import io.hydrosphere.serving.manager.domain.contract.{Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository.ApplicationRow
import io.hydrosphere.serving.proto.contract.signature.ModelSignature

class DBApplicationRepoSpec extends FullIntegrationSpec with IOChecker {
  val transactor: doobie.Transactor[IO] = app.transactor
  private val uploadFile                = packModel("/models/dummy_model")
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
  var mv1: ModelVersion.Internal = _
  var servable: Servable         = _

  describe("Queries") {
    val appRow = ApplicationRow(
      id = 1,
      application_name = "test",
      namespace = Some("namespace"),
      status = "Ready",
      application_contract = ModelSignature.defaultInstance.toProtoString,
      execution_graph = "",
      used_servables = List("asd", "q123"),
      kafka_streams = List("azxcxz"),
      status_message = Some("Ok"),
      used_model_versions = List(1, 2, 3),
      metadata = None
    )

    it("should have correct queries") {
      check(DBApplicationRepository.allQ)
      check(DBApplicationRepository.getByIdQ(1))
      check(DBApplicationRepository.getByNameQ("test"))
      check(DBApplicationRepository.updateQ(appRow))
      check(DBApplicationRepository.createQ(appRow))
      succeed
    }
  }

  describe("Methods") {
    it("should raise error on incompatible application graph") {
      pending
      // val graph = "{\"stages\":[{\"modelVariants\":[{\"modelVersion\":{\"model\":{\"id\":2,\"name\":\"claims_tgdq\"},\"image\":{\"name\":\"dev-docker-registry.k8s.hydrosphere.io/claims_tgdq\",\"tag\":\"1\",\"sha256\":\"74fe2d2e1f89c615fc11e822c969a5ee5d429cc58e0d3d83ac9c65dc7d572506\"},\"finished\":\"2019-05-28T12:34:02.688\",\"modelContract\":{\"modelName\":\"model\",\"predict\":{\"signatureName\":\"claim\",\"inputs\":[{\"profile\":\"TEXT\",\"dtype\":\"DT_STRING\",\"name\":\"foo\",\"shape\":{\"dim\":[],\"unknownRank\":false}},{\"profile\":\"NUMERICAL\",\"dtype\":\"DT_DOUBLE\",\"name\":\"client_profile\",\"shape\":{\"dim\":[{\"size\":112,\"name\":\"\"}],\"unknownRank\":false}}],\"outputs\":[{\"profile\":\"NONE\",\"dtype\":\"DT_INT64\",\"name\":\"amount\",\"shape\":{\"dim\":[],\"unknownRank\":false}}]}},\"id\":2,\"status\":\"Released\",\"profileTypes\":{},\"metadata\":{\"git.branch.head.date\":\"Tue Apr 16 10:44:31 2019\",\"git.branch.head.sha\":\"172da8da2fad6d48c49cf8afffc05010079620e8\",\"git.branch\":\"master\",\"git.branch.head.author.name\":\"Konstantin Makarychev\",\"git.is-dirty\":\"True\",\"git.branch.head.author.email\":\"mrsimpson@inbox.ru\",\"experiment\":\"demo\"},\"modelVersion\":1,\"runtime\":{\"name\":\"hydrosphere/serving-runtime-python-3.6\",\"tag\":\"dev\"},\"created\":\"2019-05-28T12:33:56.556\"},\"weight\":100}],\"signature\":{\"signatureName\":\"claim\",\"inputs\":[{\"profile\":\"TEXT\",\"dtype\":\"DT_STRING\",\"name\":\"foo\",\"shape\":{\"dim\":[],\"unknownRank\":false}},{\"profile\":\"NUMERICAL\",\"dtype\":\"DT_DOUBLE\",\"name\":\"client_profile\",\"shape\":{\"dim\":[{\"size\":112,\"name\":\"\"}],\"unknownRank\":false}}],\"outputs\":[{\"profile\":\"NONE\",\"dtype\":\"DT_INT64\",\"name\":\"amount\",\"shape\":{\"dim\":[],\"unknownRank\":false}}]}}]}"
      //      val data = ApplicationRow(1, "test", None, "Ready", "", graph, List.empty, List.empty, None, List.empty)
      //      val res = DBApplicationRepository.mapFromDb(data, Map.empty, Map.empty)
      //      assert(res.left.get.isInstanceOf[DBApplicationRepository.IncompatibleExecutionGraphError], res)
    }
    it("should create") {
      val application = Application(
        id = 0,
        name = "repo-spec-app",
        namespace = None,
        status = Application.Status.Ready,
        signature = Signature.defaultSignature,
        kafkaStreaming = List.empty,
        graph = ApplicationGraph(
          NonEmptyList.of(
            ApplicationStage(
              NonEmptyList.of(ApplicationServable(mv1, 100)),
              Signature.defaultSignature
            )
          )
        )
      )
      val result = app.core.repos.appRepo.create(application).unsafeRunSync()
      println(result)
      assert(result.id !== 0)
    }
    it("should retrieve an application by id") {
      val result = app.core.repos.appRepo.get(1).unsafeRunSync().get
      println(result)
      assert(result.name === "repo-spec-app")
    }
    it("should retrieve an application by name") {
      val result = app.core.repos.appRepo.get("repo-spec-app").unsafeRunSync().get
      println(result)
      assert(result.id === 1)
    }
    it("should find app usages") {
      val oldApp = app.core.repos.appRepo.get("repo-spec-app").unsafeRunSync().get
      val result = app.core.repos.appRepo.findServableUsage(servable.name).unsafeRunSync()
      assert(result.head.name == oldApp.name)

      val failResult = app.core.repos.appRepo.findServableUsage("hackermans").unsafeRunSync()
      assert(failResult.isEmpty, failResult)
    }
  }
  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val f = for {
      d1         <- app.core.modelService.uploadModel(uploadFile, upload1)
      completed1 <- d1.completed.get
      s = Servable(
        completed1,
        "test-suffix",
        Servable.Status.Serving,
        message = "ok".some,
        host = "localhost".some,
        port = 9090.some
      )
      _ <- app.core.repos.servableRepo.upsert(s)
    } yield {
      println(s"UPLOADED: $completed1")
      mv1 = completed1
      servable = s
    }
    f.unsafeRunSync()
  }
}

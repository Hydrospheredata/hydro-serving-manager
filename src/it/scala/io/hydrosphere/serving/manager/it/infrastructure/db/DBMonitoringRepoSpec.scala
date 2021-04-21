package io.hydrosphere.serving.manager.it.infrastructure.db

import java.time.Instant
import java.util.UUID
import cats.effect.IO
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{
  CustomModelMetricSpec,
  CustomModelMetricSpecConfiguration,
  MetricSpecEvents,
  MonitoringConfiguration,
  ThresholdCmpOperator
}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBMonitoringRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBMonitoringRepository.MetricSpecRow
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import cats.implicits._

class DBMonitoringRepoSpec extends FullIntegrationSpec with IOChecker {
  implicit val transactor = app.transactor

  var version: ModelVersion.Internal = _
  var servable: Servable             = _

  describe("Queries") {
    val id = UUID.randomUUID().toString
    val msRow = MetricSpecRow(
      id = id,
      kind = "CustomModelSpec",
      name = "test",
      modelVersionId = 1,
      config = Some("{\"field\": \"value\"}")
    )
    it("should have valid queries") {
      check(DBMonitoringRepository.allQ)
      check(DBMonitoringRepository.deleteQ(id))
      check(DBMonitoringRepository.selectByVersionIdQ(1))
      check(DBMonitoringRepository.selectByIdQ(id))
      check(DBMonitoringRepository.upsertQ(msRow))
      succeed
    }
  }

  // TODO: implicit
  describe("Methods") {
    val o    = MetricSpecEvents
    val repo = DBMonitoringRepository.make[IO]()
    it("should insert a MetricSpec") {
      val msRow = CustomModelMetricSpec(
        name = "test",
        modelVersionId = 1,
        id = "1",
        config = CustomModelMetricSpecConfiguration(
          modelVersionId = 1,
          threshold = 123,
          thresholdCmpOperator = ThresholdCmpOperator.LessEq,
          servable = None,
          deploymentConfigName = None
        )
      )
      repo.upsert(msRow).unsafeRunSync()

      val msRow2 = CustomModelMetricSpec(
        name = "metric-with-servable",
        modelVersionId = 2,
        id = "2",
        config = CustomModelMetricSpecConfiguration(
          modelVersionId = 1,
          threshold = 123,
          thresholdCmpOperator = ThresholdCmpOperator.LessEq,
          servable = Some(servable),
          deploymentConfigName = None
        )
      )
      repo.upsert(msRow2).unsafeRunSync()
      succeed
    }

    it("should delete MetricSpec") {
      repo.delete("1").unsafeRunSync()
      succeed
    }

    it("should get metric by ID") {
      val maybeResult = repo.get("2").unsafeRunSync()
      assert(maybeResult.isDefined, maybeResult)
      val result = maybeResult.get
      assert(result.id == "2")
      assert(result.modelVersionId == 2)
      assert(result.name == "metric-with-servable")
      assert(result.config.servable.get == servable)
      assert(result.config.thresholdCmpOperator == ThresholdCmpOperator.LessEq)
      assert(result.config.threshold == 123)
      assert(result.config.modelVersionId == 1)
    }

    it("should get all metrics") {
      val specs = repo.all().unsafeRunSync()
      assert(specs.length == 1)
      val result = specs.head
      assert(result.id == "2")
      assert(result.modelVersionId == 2)
      assert(result.name == "metric-with-servable")
      assert(result.config.servable.get == servable)
      assert(result.config.thresholdCmpOperator == ThresholdCmpOperator.LessEq)
      assert(result.config.threshold == 123)
      assert(result.config.modelVersionId == 1)
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val f = for {
      m <- app.core.repos.modelRepo.create(Model(1, "model-name"))
      mvOld = ModelVersion.Internal(
        1,
        DockerImage("qwe", "asdasd"),
        Instant.now(),
        Some(Instant.now()),
        1,
        Signature.defaultSignature,
        dummyImage,
        m,
        ModelVersionStatus.Released,
        None,
        Map.empty,
        MonitoringConfiguration()
      )
      mv <- app.core.repos.versionRepo.create(mvOld)
      mvNew = mvOld.copy(id = mv.id)
      serv = Servable(
        mvNew,
        "test-servable",
        Servable.Status.Serving,
        "ok".some,
        "here".some,
        90.some
      )
      res <- app.core.repos.servableRepo.upsert(serv)
    } yield {
      println(s"Created: $mv")
      println(s"Created: $res")
      version = mvNew
      servable = res
    }
    f.unsafeRunSync()
  }
}

package io.hydrosphere.serving.manager.domain.application

import java.time.LocalDateTime

import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.IO
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.graph.Variant
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.requests.ExecutionGraphRequest
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.util.DeferredResult

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

class ApplicationMigrationToolSpec extends GenericUnitTest {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  describe("ApplicationMigrationTool") {
    it("should detect and recover invalid apps") {
      val graph = "{\"stages\":[{\"modelVariants\":[{\"modelVersion\":{\"model\":{\"id\":1,\"name\":\"claims\"},\"image\":{\"name\":\"claims_tgdq\",\"tag\":\"1\",\"sha256\":\"74fe2d2\"},\"finished\":\"2019-05-28T12:34:02.688\",\"modelContract\":{\"modelName\":\"model\",\"predict\":{\"signatureName\":\"claim\",\"inputs\":[{\"profile\":\"TEXT\",\"dtype\":\"DT_STRING\",\"name\":\"foo\",\"shape\":{\"dim\":[],\"unknownRank\":false}},{\"profile\":\"NUMERICAL\",\"dtype\":\"DT_DOUBLE\",\"name\":\"client_profile\",\"shape\":{\"dim\":[{\"size\":112,\"name\":\"\"}],\"unknownRank\":false}}],\"outputs\":[{\"profile\":\"NONE\",\"dtype\":\"DT_INT64\",\"name\":\"amount\",\"shape\":{\"dim\":[],\"unknownRank\":false}}]}},\"id\":2,\"status\":\"Released\",\"profileTypes\":{},\"metadata\":{\"git.branch.head.date\":\"Tue Apr 16 10:44:31 2019\",\"git.branch.head.sha\":\"172da8da2fad6d48c49cf8afffc05010079620e8\",\"git.branch\":\"master\",\"git.branch.head.author.name\":\"Konstantin Makarychev\",\"git.is-dirty\":\"True\",\"git.branch.head.author.email\":\"mrsimpson@inbox.ru\",\"experiment\":\"demo\"},\"modelVersion\":1,\"runtime\":{\"name\":\"hydrosphere/serving-runtime-python-3.6\",\"tag\":\"dev\"},\"created\":\"2019-05-28T12:33:56.556\"},\"weight\":100}],\"signature\":{\"signatureName\":\"claim\",\"inputs\":[{\"profile\":\"TEXT\",\"dtype\":\"DT_STRING\",\"name\":\"foo\",\"shape\":{\"dim\":[],\"unknownRank\":false}},{\"profile\":\"NUMERICAL\",\"dtype\":\"DT_DOUBLE\",\"name\":\"client_profile\",\"shape\":{\"dim\":[{\"size\":112,\"name\":\"\"}],\"unknownRank\":false}}],\"outputs\":[{\"profile\":\"NONE\",\"dtype\":\"DT_INT64\",\"name\":\"amount\",\"shape\":{\"dim\":[],\"unknownRank\":false}}]}}]}"
      val data = Tables.ApplicationRow(1, "test", None, "Ready", "", graph, List.empty, List.empty, None)
      val removedApps = ListBuffer.empty[Long]
      val appsRepo = new ApplicationRepository[IO] {
        override def create(entity: GenericApplication): IO[GenericApplication] = ???
        override def get(id: Long): IO[Option[GenericApplication]] = ???
        override def get(name: String): IO[Option[GenericApplication]] = ???
        override def update(value: GenericApplication): IO[Int] = ???
        override def delete(id: Long): IO[Int] = IO(removedApps += id).as(1)
        override def all(): IO[List[GenericApplication]] = {
          IO.fromEither(DBApplicationRepository.mapFromDb(data, Map.empty).map(List(_)))
        }
        override def applicationsWithCommonServices(servables: Set[GenericServable], applicationId: Long): IO[List[GenericApplication]] = ???
        override def findVersionsUsage(versionIdx: Long): IO[List[GenericApplication]] = ???
      }
      val cd = CloudInstance(1, "aaa", CloudInstance.Status.Running("host", 9090))
      val removed = ListBuffer.empty[String]
      val cloudDriver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???
        override def instance(name: String): IO[Option[CloudInstance]] = ???
        override def run(name: String, modelVersionId: Long, image: DockerImage): IO[CloudInstance] = ???
        override def remove(name: String): IO[Unit] = IO(removed += name)
        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = {
          IO(cd.some)
        }
      }
      val modelVersion = ModelVersion(1, DockerImage("asd", "asd"), LocalDateTime.now(), None, 1,
        ModelContract.defaultInstance, DockerImage("rrr", "rrr"), Model(1, "aaa"), None,
        ModelVersionStatus.Released, Map.empty, None, Map.empty
      )
      val appGraph = NonEmptyList.of(
        PipelineStage(
          modelVariants = NonEmptyList.of(
            Variant(
              item = modelVersion,
              weight = 100
            )
          ),
          signature = ModelSignature.defaultInstance
        )
      )
      val app = Application(1, "test", None, Application.Assembling(appGraph), ModelSignature.defaultInstance, Nil)
      val appDeployer = new ApplicationDeployer[IO] {
        override def deploy(name: String, executionGraph: ExecutionGraphRequest, kafkaStreaming: List[ApplicationKafkaStream]): IO[DeferredResult[IO, GenericApplication]] = {
          DeferredResult.completed(app)
        }
      }
      val mt = ApplicationMigrationTool.default[IO](appsRepo, cloudDriver, appDeployer)
      val result = mt.getAndRevive().unsafeRunSync()
      assert(result.nonEmpty, "apps")
      assert(removed.nonEmpty, "instancesremoved")
      assert(removedApps.nonEmpty, "appsremoved")
    }
  }
}

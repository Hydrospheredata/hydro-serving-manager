package io.hydrosphere.serving.manager

import java.time.Instant

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.IO
import cats.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.FlywayClient
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository._
import io.hydrosphere.serving.manager.util.DeferredResult

import scala.collection.mutable.ListBuffer

class DBStarterSpec extends GenericUnitTest {
  val modelVersion = ModelVersion.Internal(
    1,
    DockerImage("asd", "asd"),
    Instant.now(),
    None,
    1,
    defaultContract,
    dummyImage,
    Model(1, "aaa"),
    None,
    ModelVersionStatus.Released,
    None,
    Map.empty
  )
  val appGraph = ApplicationGraph(
    NonEmptyList.of(
      WeightedNode(
        NonEmptyList.of(
          Variant(
            modelVersion,
            None,
            100
          )
        ),
        defaultContract.predict
      )
    ),
    defaultContract.predict
  )
  val app = Application(
    1,
    "test",
    None,
    Application.Status.Assembling,
    Nil,
    appGraph,
    "ok",
    Map.empty
  )

  describe("ApplicationMigrationTool") {
    it("should detect and recover invalid apps") {
      val graph =
        "{\"stages\":[{\"modelVariants\":[{\"modelVersion\":{\"model\":{\"id\":1,\"name\":\"claims\"},\"image\":{\"name\":\"claims_tgdq\",\"tag\":\"1\",\"sha256\":\"74fe2d2\"},\"finished\":\"2019-05-28T12:34:02.688\",\"modelContract\":{\"modelName\":\"model\",\"predict\":{\"signatureName\":\"claim\",\"inputs\":[{\"profile\":\"TEXT\",\"dtype\":\"DT_STRING\",\"name\":\"foo\",\"shape\":{\"dim\":[],\"unknownRank\":false}},{\"profile\":\"NUMERICAL\",\"dtype\":\"DT_DOUBLE\",\"name\":\"client_profile\",\"shape\":{\"dim\":[{\"size\":112,\"name\":\"\"}],\"unknownRank\":false}}],\"outputs\":[{\"profile\":\"NONE\",\"dtype\":\"DT_INT64\",\"name\":\"amount\",\"shape\":{\"dim\":[],\"unknownRank\":false}}]}},\"id\":2,\"status\":\"Released\",\"profileTypes\":{},\"metadata\":{\"git.branch.head.date\":\"Tue Apr 16 10:44:31 2019\",\"git.branch.head.sha\":\"172da8da2fad6d48c49cf8afffc05010079620e8\",\"git.branch\":\"master\",\"git.branch.head.author.name\":\"Konstantin Makarychev\",\"git.is-dirty\":\"True\",\"git.branch.head.author.email\":\"mrsimpson@inbox.ru\",\"experiment\":\"demo\"},\"modelVersion\":1,\"runtime\":{\"name\":\"hydrosphere/serving-runtime-python-3.6\",\"tag\":\"dev\"},\"created\":\"2019-05-28T12:33:56.556\"},\"weight\":100}],\"signature\":{\"signatureName\":\"claim\",\"inputs\":[{\"profile\":\"TEXT\",\"dtype\":\"DT_STRING\",\"name\":\"foo\",\"shape\":{\"dim\":[],\"unknownRank\":false}},{\"profile\":\"NUMERICAL\",\"dtype\":\"DT_DOUBLE\",\"name\":\"client_profile\",\"shape\":{\"dim\":[{\"size\":112,\"name\":\"\"}],\"unknownRank\":false}}],\"outputs\":[{\"profile\":\"NONE\",\"dtype\":\"DT_INT64\",\"name\":\"amount\",\"shape\":{\"dim\":[],\"unknownRank\":false}}]}}]}"
      val data1 = ApplicationRow(
        id = 1,
        application_name = "test",
        namespace = None,
        status = "Ready",
        application_contract = defaultContract.predict,
        execution_graph = graph,
        used_servables = Nil,
        kafka_streams = Nil,
        status_message = None,
        used_model_versions = Nil,
        metadata = None
      )

      val removedApps = ListBuffer.empty[Long]
      val appsRepo    = mock[ApplicationRepository[IO]]
      when(appsRepo.delete(any)).thenAnswer[Long](x => IO(removedApps += x).as(1))
      when(appsRepo.all()).thenReturn(
        IO.fromEither {
          List(data1, data1)
            .traverse(x =>
              DBApplicationRepository.toApplication(x, Map.empty, Map.empty).toValidatedNec
            )
            .leftMap(errors => AppDBSchemaErrors(errors.toList))
            .toEither
        }
      )

      val cd          = CloudInstance(1, "aaa", CloudInstance.Status.Running("host", 9090))
      val removed     = ListBuffer.empty[String]
      val cloudDriver = mock[CloudDriver[IO]]
      when(cloudDriver.remove(any)).thenAnswer[String](name => IO(removed += name))
      when(cloudDriver.getByVersionId(anyLong)).thenReturn(cd.some.pure[IO])

      val appDeployer = mock[ApplicationDeployer[IO]]
      when(appDeployer.deploy(any, any, any)).thenReturn(DeferredResult.completed(app))

      val serviceRepo = mock[ServableRepository[IO]]
      val flyway      = mock[FlywayClient[IO]]
      val tx          = mock[Transactor[IO]]

      val mt = DBStarter.make[IO](appsRepo, cloudDriver, appDeployer, serviceRepo, flyway, tx)
      mt.checkApplicationGraphs().unsafeRunSync()
      assert(removed.nonEmpty, "instancesremoved")
      assert(removedApps.nonEmpty, "appsremoved")
    }

    it("should fill absent model versions") {
      val graph =
        """
          |{
          |   "stages":[
          |      {
          |         "modelVariants":[
          |            {
          |               "item":"claims-model-2-dusty-wind",
          |               "weight":100
          |            }
          |         ],
          |         "signature":{
          |            "signatureName":"claim",
          |            "inputs":[
          |               {
          |                  "profile":"TEXT",
          |                  "dtype":"DT_DOUBLE",
          |                  "name":"client_profile",
          |                  "shape":{
          |                     "dim":[
          |                        {
          |                           "size":112,
          |                           "name":""
          |                        }
          |                     ],
          |                     "unknownRank":false
          |                  }
          |               }
          |            ],
          |            "outputs":[
          |               {
          |                  "profile":"NONE",
          |                  "dtype":"DT_INT64",
          |                  "name":"amount",
          |                  "shape":{
          |                     "dim":[
          |
          |                     ],
          |                     "unknownRank":false
          |                  }
          |               }
          |            ]
          |         }
          |      }
          |   ]
          |}
        """.stripMargin

      val data1 = ApplicationRow(
        id = 1,
        application_name = "test",
        namespace = None,
        status = "Ready",
        application_contract = defaultContract.predict,
        execution_graph = graph,
        used_servables = Nil,
        kafka_streams = Nil,
        status_message = None,
        used_model_versions = Nil,
        metadata = None
      )

      val servable = Servable(
        modelVersion,
        "kek",
        Servable.Status.Serving,
        Nil,
        "Ok",
        "host".some,
        9090.some
      )
      val sMap = Map(
        "claims-model-2-dusty-wind" -> servable
      )

      val appsRepo = mock[ApplicationRepository[IO]]
      when(appsRepo.all()).thenReturn(
        IO.fromEither {
          List(data1, data1)
            .traverse(x =>
              DBApplicationRepository.toApplication(x, Map.empty, Map.empty).toValidatedNec
            )
            .leftMap(errors => AppDBSchemaErrors(errors.toList))
            .toEither
        }
      )

      val cd          = CloudInstance(1, "aaa", CloudInstance.Status.Running("host", 9090))
      val cloudDriver = mock[CloudDriver[IO]]
      when(cloudDriver.getByVersionId(anyLong)).thenReturn(cd.some.pure[IO])

      val appDeployer = mock[ApplicationDeployer[IO]]
      when(appDeployer.deploy(any, any, any)).thenReturn(DeferredResult.completed(app))

      val serviceRepo = mock[ServableRepository[IO]]
      when(serviceRepo.get(any[NonEmptySet[String]])).thenReturn(sMap.values.toList.pure[IO])

      val flyway = mock[FlywayClient[IO]]
      val tx     = mock[Transactor[IO]]
      val mt     = DBStarter.make[IO](appsRepo, cloudDriver, appDeployer, serviceRepo, flyway, tx)
      mt.checkApplicationGraphs().unsafeRunSync()
      assert(true)
    }
  }
}

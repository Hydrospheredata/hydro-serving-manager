package io.hydrosphere.serving.manager.domain

import cats.data.{NonEmptyList, OptionT}
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, IO, Timer}
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.config.DefaultDeploymentConfiguration
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfiguration,
  DeploymentConfigurationService
}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringRepository
import io.hydrosphere.serving.manager.domain.servable._
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBServableRepository
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import org.mockito.Matchers

import java.time.Instant
import scala.concurrent.ExecutionContext

// TODO

class ServableSpec extends GenericUnitTest {
  implicit val rng: RNG[IO]               = RNG.default[IO].unsafeRunSync()
  implicit val nameGen: NameGenerator[IO] = NameGenerator.haiku[IO]()
  implicit val uuidGen: UUIDGenerator[IO] = UUIDGenerator.default[IO]()
  implicit val timer: Timer[IO]           = IO.timer(ExecutionContext.global)

  val signature: Signature = Signature(
    signatureName = "test",
    inputs = NonEmptyList.of(
      Field.Tensor("a", DataType.DT_STRING, TensorShape.varVector)
    ),
    outputs = NonEmptyList.of(
      Field.Tensor("b", DataType.DT_STRING, TensorShape.varVector)
    )
  )

  val externalMv: ModelVersion.External = ModelVersion.External(
    id = 1,
    created = Instant.now(),
    modelVersion = 1,
    modelSignature = signature,
    model = Model(1, "name"),
    metadata = Map.empty
  )

  val mv: ModelVersion.Internal = ModelVersion.Internal(
    id = 10,
    image = DockerImage("name", "tag"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelSignature = signature,
    runtime = DockerImage("runtime", "tag"),
    model = Model(1, "name"),
    status = ModelVersionStatus.Released,
    installCommand = None,
    metadata = Map.empty
  )
  val servable: Servable =
    Servable(
      mv,
      "test",
      Servable.Status.Starting,
      "msg".some,
      None,
      None,
      deploymentConfiguration = DeploymentConfiguration.empty
    )

  describe("Default Deployment Configuration") {
    val defaultDC = DefaultDeploymentConfiguration(
      container = None,
      pod = None,
      deployment = None,
      hpa = None
    ).toDC

    it("should use it if no DC specified for servable") {
      implicit val servableRepo: ServableRepository[IO] = mock[ServableRepository[IO]]
      when(servableRepo.get(any[String])).thenReturn(None.pure[IO])
      when(servableRepo.upsert(any)).thenReturn(servable.pure[IO])

      implicit val appRepo: ApplicationRepository[IO]      = mock[ApplicationRepository[IO]]
      implicit val versionRepo: ModelVersionRepository[IO] = mock[ModelVersionRepository[IO]]
      implicit val monRepo: MonitoringRepository[IO]       = mock[MonitoringRepository[IO]]
      implicit val depConf: DeploymentConfigurationService[IO] =
        mock[DeploymentConfigurationService[IO]]

      implicit val cloudDriver: CloudDriver[IO] = mock[CloudDriver[IO]]
      val cloudInstance                         = CloudInstance(1, "kek", CloudInstance.Status.Starting)
      when(
        cloudDriver.run(
          name = any[String],
          modelVersionId = anyLong,
          image = any,
          config = eqTo(defaultDC)
        )
      ).thenReturn(IO(cloudInstance))

      val servableService = ServableService[IO](
        defaultDC = defaultDC
      )
      val res = servableService
        .deploy(
          modelVersion = mv,
          deployConfig = None,
          metadata = Map.empty
        )
        .unsafeRunSync()
      assert(res.deploymentConfiguration.name == defaultDC.name)
    }

    it("should not use it if DC specified for servable") {
      val customDC = DeploymentConfiguration(
        name = "custom-config",
        container = None,
        pod = None,
        deployment = None,
        hpa = None
      )

      implicit val servableRepo: ServableRepository[IO] = mock[ServableRepository[IO]]
      when(servableRepo.get(any[String])).thenReturn(None.pure[IO])
      when(servableRepo.upsert(any)) thenAnswer ((s: Servable) => {
        IO(s)
      })

      implicit val appRepo: ApplicationRepository[IO]      = mock[ApplicationRepository[IO]]
      implicit val versionRepo: ModelVersionRepository[IO] = mock[ModelVersionRepository[IO]]
      implicit val monRepo: MonitoringRepository[IO]       = mock[MonitoringRepository[IO]]
      implicit val depConf: DeploymentConfigurationService[IO] =
        mock[DeploymentConfigurationService[IO]]

      implicit val cloudDriver: CloudDriver[IO] = mock[CloudDriver[IO]]
      val cloudInstance                         = CloudInstance(1, "kek", CloudInstance.Status.Starting)
      when(
        cloudDriver.run(
          name = any[String],
          modelVersionId = anyLong,
          image = any,
          config = any
        )
      ).thenReturn(IO(cloudInstance))

      val servableService = ServableService[IO](
        defaultDC = defaultDC
      )

      val res = servableService
        .deploy(
          modelVersion = mv,
          deployConfig = customDC.some,
          metadata = Map.empty
        )
        .unsafeRunSync()

      assert(res.deploymentConfiguration.name == customDC.name)
    }
  }
}

//class ServableSpec extends GenericUnitTest {
//  implicit val rng: RNG[IO]               = RNG.default[IO].unsafeRunSync()
//  implicit val nameGen: NameGenerator[IO] = NameGenerator.haiku[IO]()
//  implicit val uuidGen: UUIDGenerator[IO] = UUIDGenerator.default[IO]()
//  implicit val timer: Timer[IO]           = IO.timer(ExecutionContext.global)
//
//  val signature = Signature(
//    signatureName = "test",
//    inputs = NonEmptyList.of(
//      Field.Tensor("a", DataType.DT_STRING, TensorShape.varVector)
//    ),
//    outputs = NonEmptyList.of(
//      Field.Tensor("b", DataType.DT_STRING, TensorShape.varVector)
//    )
//  )
//
//  val externalMv = ModelVersion.External(
//    id = 1,
//    created = Instant.now(),
//    modelVersion = 1,
//    modelSignature = signature,
//    model = Model(1, "name"),
//    metadata = Map.empty
//  )
//
//  val mv = ModelVersion.Internal(
//    id = 10,
//    image = DockerImage("name", "tag"),
//    created = Instant.now(),
//    finished = None,
//    modelVersion = 1,
//    modelSignature = signature,
//    runtime = DockerImage("runtime", "tag"),
//    model = Model(1, "name"),
//    status = ModelVersionStatus.Released,
//    installCommand = None,
//    metadata = Map.empty
//  )
//  val servable = Servable(mv, "test", Servable.Status.Starting, "msg", None, None)
//
//  describe("CRUD") {
//    it("should not deploy an external ModelVersion") {
//      val depConfService = mock[DeploymentConfigurationService[IO]]
//
//      val cloudDriver = mock[CloudDriver[IO]]
//      when(cloudDriver.run(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
//        .thenReturn(IO.raiseError(new IllegalStateException("Shouldn't reach this")))
//
//      val servableRepo = mock[ServableRepository[IO]]
//
//      val versionRepo = mock[ModelVersionRepository[IO]]
//      when(versionRepo.get(externalMv.id)).thenReturn(externalMv.some.pure[IO])
//
//      val appRepo = mock[ApplicationRepository[IO]]
//
//      val monitoringRepo = mock[MonitoringRepository[IO]]
//
//      val service = ServableService[IO](None)(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        cloudDriver,
//        servableRepo,
//        appRepo,
//        versionRepo,
//        monitoringRepo,
//        depConfService
//      )
//      val deployResult = service.findAndDeploy(1, None, Map.empty).attempt.unsafeRunSync()
//      deployResult.left.value should be(
//        DomainError.invalidRequest(
//          s"Deployment of external model is unavailable. modelVersionId=${externalMv.id} name=${externalMv.fullName}"
//        )
//      )
//    }
//
//    it("should be able to create Servable") {
//      val depConfService = mock[DeploymentConfigurationService[IO]]
//      val driverState    = ListBuffer.empty[CloudInstance]
//      val cloudDriver    = mock[CloudDriver[IO]]
//      when(cloudDriver.run(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
//        .thenAnswer {
//          (name: String, id: Long, _: DockerImage, _: Option[DeploymentConfiguration]) =>
//            val instance = CloudInstance(id, name, CloudInstance.Status.Starting)
//            driverState += instance
//            IO(instance)
//        }
//      val repoState    = ListBuffer.empty[Servable]
//      val servableRepo = mock[ServableRepository[IO]]
//      when(servableRepo.upsert(any)).thenAnswer { (servable: Servable) =>
//        repoState += servable
//        IO(servable)
//      }
//      when(servableRepo.get(any[String])).thenAnswer { (name: String) =>
//        IO(repoState.find(_.fullName == name))
//      }
//      when(servableRepo.all()).thenAnswer(repoState.toList.pure[IO])
//
//      val versionRepo  = mock[ModelVersionRepository[IO]]
//      val monitorState = ListBuffer.empty[Servable.Status]
//      val appRepo      = mock[ApplicationRepository[IO]]
//      val service = ServableService[IO](None)(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        cloudDriver,
//        servableRepo,
//        appRepo,
//        versionRepo,
//        null,
//        depConfService
//      )
//      val result = service.deploy(mv, None, Map.empty).unsafeRunSync().completed.get.unsafeRunSync()
//      assert(result.modelVersion === mv)
//      assert(driverState.nonEmpty)
//      assert(monitorState.nonEmpty)
//      assert(repoState.nonEmpty)
//    }
//
//    it("should be able to delete Servable") {
//      val mv = ModelVersion.Internal(
//        id = 10,
//        image = DockerImage("name", "tag"),
//        created = Instant.now(),
//        finished = None,
//        modelVersion = 1,
//        modelSignature = signature,
//        runtime = DockerImage("runtime", "tag"),
//        model = Model(1, "test-model"),
//        status = ModelVersionStatus.Assembling,
//        installCommand = None,
//        metadata = Map.empty
//      )
//
//      val initServable = Servable(
//        modelVersion = mv,
//        nameSuffix = "delete-me",
//        status = Servable.Status.Serving,
//        usedApps = Nil,
//        message = "Ok",
//        host = "host".some,
//        port = 9090.some
//      )
//
//      val driverState = ListBuffer.empty[String]
//      val cloudDriver = mock[CloudDriver[IO]]
//      when(cloudDriver.remove(any)).thenAnswer { name: String =>
//        driverState += name
//        IO.unit
//      }
//
//      val repoState = ListBuffer.empty[Servable]
//      repoState += initServable
//      val servableRepo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]] = IO(repoState.toList)
//        override def upsert(servable: Servable): IO[Servable] = {
//          repoState += servable
//          IO(servable)
//        }
//        override def delete(name: String): IO[Int] =
//          OptionT(get(name))
//            .map(x => repoState -= x)
//            .map(_ => 1)
//            .getOrElseF(IO.raiseError(new Exception(s"SERVABLE $name NOT FOUND")))
//        override def get(name: String): IO[Option[Servable]] =
//          IO(repoState.find(_.fullName == name))
//        override def get(names: Seq[String]): IO[List[Servable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[Servable]] = ???
//      }
//      val versionRepo = new ModelVersionRepository[IO] {
//        override def create(entity: ModelVersion): IO[ModelVersion]                       = ???
//        override def get(id: Long): IO[Option[ModelVersion]]                              = ???
//        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
//        override def delete(id: Long): IO[Int]                                            = ???
//        override def all(): IO[List[ModelVersion]]                                        = ???
//        override def listForModel(modelId: Long): IO[List[ModelVersion]]                  = ???
//        override def update(entity: ModelVersion): IO[Int]                                = ???
//        override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]]     = ???
//      }
//      val monitor = new ServableMonitor[IO] {
//        override def monitor(name: Servable) = ???
//      }
//
//      val appRepo = new ApplicationRepository[IO] {
//        override def create(entity: Application): IO[Application]                   = ???
//        override def get(id: Long): IO[Option[Application]]                         = ???
//        override def get(name: String): IO[Option[Application]]                     = ???
//        override def update(value: Application): IO[Int]                            = ???
//        override def delete(id: Long): IO[Int]                                      = ???
//        override def all(): IO[List[Application]]                                   = ???
//        override def findVersionUsage(versionIdx: Long): IO[List[Application]]      = ???
//        override def findServableUsage(servableName: String): IO[List[Application]] = IO(Nil)
//      }
//      val service = ServableService[IO]()(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        cloudDriver,
//        servableRepo,
//        appRepo,
//        versionRepo,
//        monitor,
//        null,
//        depConfService
//      )
//      val result = service.stop("test-model-1-delete-me").unsafeRunSync()
//      assert(result.modelVersion === mv)
//      driverState should not be empty
//      repoState shouldBe empty
//    }
//
//    it("should reject deletion of used Servable") {
//      val mv = ModelVersion.Internal(
//        id = 10,
//        image = DockerImage("name", "tag"),
//        created = Instant.now(),
//        finished = None,
//        modelVersion = 1,
//        modelContract = ModelContract.defaultInstance,
//        runtime = DockerImage("runtime", "tag"),
//        model = Model(1, "test-model"),
//        status = ModelVersionStatus.Assembling,
//        installCommand = None,
//        metadata = Map.empty
//      )
//
//      val initServable = Servable(
//        modelVersion = mv,
//        nameSuffix = "delete-me",
//        Servable.Serving("Ok", "host", 9090),
//        Nil
//      )
//
//      val driverState = ListBuffer.empty[String]
//      val cloudDriver = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]]                = ???
//        override def instance(name: String): IO[Option[CloudInstance]] = ???
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration] = None
//        ) = ???
//        override def remove(name: String): IO[Unit] = {
//          driverState += name
//          IO.unit
//        }
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String] = ???
//      }
//      val repoState = ListBuffer.empty[Servable]
//      repoState += initServable
//      val servableRepo = new ServableRepository[IO] {
//        override def all(): IO[List[GenericServable]] = IO(repoState.toList)
//        override def upsert(servable: GenericServable): IO[GenericServable] = {
//          repoState += servable
//          IO(servable)
//        }
//        override def delete(name: String): IO[Int] =
//          OptionT(get(name))
//            .map(x => repoState -= x)
//            .map(_ => 1)
//            .getOrElseF(IO.raiseError(new Exception(s"SERVABLE $name NOT FOUND")))
//        override def get(name: String): IO[Option[GenericServable]] =
//          IO(repoState.find(_.fullName == name))
//        override def get(names: Seq[String]): IO[List[GenericServable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[GenericServable]] = ???
//      }
//      val versionRepo = new ModelVersionRepository[IO] {
//        override def create(entity: ModelVersion): IO[ModelVersion]                       = ???
//        override def update(entity: ModelVersion): IO[Int]                                = ???
//        override def get(id: Long): IO[Option[ModelVersion]]                              = ???
//        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
//        override def delete(id: Long): IO[Int]                                            = ???
//        override def all(): IO[List[ModelVersion]]                                        = ???
//        override def listForModel(modelId: Long): IO[List[ModelVersion]]                  = ???
//        override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]]     = ???
//      }
//      val monitor = new ServableMonitor[IO] {
//        override def monitor(name: GenericServable) = ???
//      }
//
//      val appRepo = new ApplicationRepository[IO] {
//        override def create(entity: Application): IO[Application] = ???
//        override def get(id: Long): IO[Option[Application]]       = ???
//        override def get(name: String): IO[Option[Application]]   = ???
//        override def update(value: Application): IO[Int]          = ???
//        override def delete(id: Long): IO[Int]                    = ???
//        override def all(): IO[List[Application]]                 = ???
//        override def findServableUsage(servableName: String): IO[List[Application]] =
//          IO {
//            List(
//              Application(
//                1,
//                "test",
//                None,
//                Application.Ready,
//                None,
//                ModelSignature.defaultInstance,
//                Nil,
//                null,
//                Map.empty
//              )
//            )
//          }
//        override def findVersionUsage(versionIdx: Long): IO[List[Application]] = ???
//      }
//      val service = ServableService[IO]()(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        cloudDriver,
//        servableRepo,
//        appRepo,
//        versionRepo,
//        monitor,
//        null,
//        depConfService
//      )
//      val result = service.stop("test-model-1-delete-me").attempt.unsafeRunSync()
//      assert(result.isLeft, result)
//    }
//
//    it("should be able to filter servables") {
//      val mv = ModelVersion.Internal(
//        id = 1,
//        image = DockerImage("name", "tag"),
//        created = Instant.now(),
//        finished = None,
//        modelVersion = 1,
//        modelContract = ModelContract.defaultInstance,
//        runtime = DockerImage("runtime", "tag"),
//        model = Model(1, "model"),
//        status = ModelVersionStatus.Assembling,
//        installCommand = None,
//        metadata = Map.empty
//      )
//
//      val s1 = Servable(
//        modelVersion = mv,
//        nameSuffix = "kek",
//        status = Servable.Serving("Ok", "host", 9090),
//        usedApps = Nil,
//        metadata = Map("author" -> "me", "date" -> "now")
//      )
//      val s2 = Servable(
//        modelVersion = mv,
//        nameSuffix = "lol",
//        status = Servable.Serving("Ok", "host", 9090),
//        usedApps = Nil,
//        metadata = Map("author" -> "you", "date" -> "now")
//      )
//      val s3 = Servable(
//        modelVersion = mv,
//        nameSuffix = "foo",
//        status = Servable.Serving("Ok", "host", 9090),
//        usedApps = Nil,
//        metadata = Map("author" -> "me", "date" -> "yesterday")
//      )
//      val servableRepo = new ServableRepository[IO] {
//        def all(): IO[List[Servable.GenericServable]]                                = IO(List(s1, s2, s3))
//        def upsert(servable: Servable.GenericServable): IO[Servable.GenericServable] = ???
//        def delete(name: String): IO[Int]                                            = ???
//        def get(name: String): IO[Option[Servable.GenericServable]]                  = ???
//        def get(names: Seq[String]): IO[List[Servable.GenericServable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[GenericServable]] = ???
//      }
//
//      val service = ServableService[IO]()(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        null,
//        servableRepo,
//        null,
//        null,
//        null,
//        null,
//        depConfService
//      )
//      val r1 = service.getFiltered(Some("model-1-kek"), None, Map.empty).unsafeRunSync()
//      assert(r1 == List(s1))
//      val r2 = service.getFiltered(None, Some(1), Map.empty).unsafeRunSync()
//      assert(r2 == List(s1, s2, s3))
//      val r3 =
//        service.getFiltered(None, None, Map("author" -> "me", "date" -> "now")).unsafeRunSync()
//      assert(r3 == List(s1))
//      val r4 = service
//        .getFiltered(Some("model-1-foo"), Some(1), Map("author" -> "me", "date" -> "yesterday"))
//        .unsafeRunSync()
//      assert(r4 == List(s3))
//    }
//
//    it("should be able to deploy ModelVersion") {
//      val mv = ModelVersion.Internal(
//        id = 1,
//        image = DockerImage("name", "tag"),
//        created = Instant.now(),
//        finished = None,
//        modelVersion = 1,
//        modelContract = ModelContract.defaultInstance,
//        runtime = DockerImage("runtime", "tag"),
//        model = Model(1, "model"),
//        status = ModelVersionStatus.Released,
//        installCommand = None,
//        metadata = Map.empty
//      )
//
//      val servableMonitor = new ServableMonitor[IO] {
//        override def monitor(servable: GenericServable): IO[Deferred[IO, GenericServable]] =
//          for {
//            d <- Deferred[IO, GenericServable]
//            _ <- d.complete(servable)
//          } yield d
//      }
//
//      val cd = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]]                = ???
//        override def instance(name: String): IO[Option[CloudInstance]] = ???
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration]
//        ): IO[CloudInstance] =
//          IO(CloudInstance(modelVersionId, name, CloudInstance.Status.Running("localhost", 9090)))
//        override def remove(name: String): IO[Unit]                                  = ???
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String] = ???
//      }
//
//      val versionRepo = new ModelVersionRepository[IO] {
//        override def create(entity: ModelVersion): IO[ModelVersion]                       = ???
//        override def update(entity: ModelVersion): IO[Int]                                = ???
//        override def get(id: Long): IO[Option[ModelVersion]]                              = IO(mv.some)
//        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
//        override def delete(id: Long): IO[Int]                                            = ???
//        override def all(): IO[List[ModelVersion]]                                        = ???
//        override def listForModel(modelId: Long): IO[List[ModelVersion]]                  = ???
//        override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]]     = ???
//      }
//
//      val servableRepo = new ServableRepository[IO] {
//        def all(): IO[List[Servable.GenericServable]]                                = IO(Nil)
//        def upsert(servable: Servable.GenericServable): IO[Servable.GenericServable] = IO(servable)
//        def delete(name: String): IO[Int]                                            = ???
//        def get(name: String): IO[Option[Servable.GenericServable]]                  = IO(None)
//        def get(names: Seq[String]): IO[List[Servable.GenericServable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[GenericServable]] = ???
//      }
//
//      val service = ServableService[IO]()(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        cd,
//        servableRepo,
//        null,
//        versionRepo,
//        servableMonitor,
//        null,
//        depConfService
//      )
//      val metadata = Map("author" -> "me", "date" -> "now")
//      val result   = service.findAndDeploy(1, None, metadata).unsafeRunSync()
//      assert(result.started.metadata == metadata)
//      assert(result.completed.get.unsafeRunSync().metadata == metadata)
//    }
//
//    it("should not deploy ModelVersion which is not Released") {
//      val mv = ModelVersion.Internal(
//        id = 1,
//        image = DockerImage("name", "tag"),
//        created = Instant.now(),
//        finished = None,
//        modelVersion = 1,
//        modelContract = ModelContract.defaultInstance,
//        runtime = DockerImage("runtime", "tag"),
//        model = Model(1, "model"),
//        status = ModelVersionStatus.Assembling,
//        installCommand = None,
//        metadata = Map.empty
//      )
//
//      val servableMonitor = new ServableMonitor[IO] {
//        override def monitor(servable: GenericServable): IO[Deferred[IO, GenericServable]] =
//          for {
//            d <- Deferred[IO, GenericServable]
//            _ <- d.complete(servable)
//          } yield d
//      }
//
//      val cd = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]]                = ???
//        override def instance(name: String): IO[Option[CloudInstance]] = ???
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration]
//        ): IO[CloudInstance] =
//          IO(CloudInstance(modelVersionId, name, CloudInstance.Status.Running("localhost", 9090)))
//        override def remove(name: String): IO[Unit]                                  = ???
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String] = ???
//      }
//
//      val versionRepo = new ModelVersionRepository[IO] {
//        override def create(entity: ModelVersion): IO[ModelVersion]                       = ???
//        override def update(entity: ModelVersion): IO[Int]                                = ???
//        override def get(id: Long): IO[Option[ModelVersion]]                              = IO(mv.some)
//        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
//        override def delete(id: Long): IO[Int]                                            = ???
//        override def all(): IO[List[ModelVersion]]                                        = ???
//        override def listForModel(modelId: Long): IO[List[ModelVersion]]                  = ???
//        override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]]     = ???
//      }
//
//      val servableRepo = new ServableRepository[IO] {
//        def all(): IO[List[Servable.GenericServable]]                                = IO(Nil)
//        def upsert(servable: Servable.GenericServable): IO[Servable.GenericServable] = IO(servable)
//        def delete(name: String): IO[Int]                                            = ???
//        def get(name: String): IO[Option[Servable.GenericServable]]                  = IO(None)
//        def get(names: Seq[String]): IO[List[Servable.GenericServable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[GenericServable]] = ???
//      }
//
//      val service = ServableService[IO]()(
//        Concurrent[IO],
//        timer,
//        nameGen,
//        uuidGen,
//        cd,
//        servableRepo,
//        null,
//        versionRepo,
//        servableMonitor,
//        null,
//        depConfService
//      )
//      val result = service.findAndDeploy(1, None, Map.empty).attempt.unsafeRunSync()
//      assert(result.isLeft)
//    }
//  }
//}

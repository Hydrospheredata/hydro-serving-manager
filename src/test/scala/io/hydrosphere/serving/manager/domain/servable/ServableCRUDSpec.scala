package io.hydrosphere.serving.manager.domain.servable

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringRepository

import scala.collection.mutable.ListBuffer

class ServableCRUDSpec extends GenericUnitTest {
  val externalMv: ModelVersion.External = ModelVersion.External(
    id = 1,
    created = Instant.now(),
    modelVersion = 1,
    modelContract = defaultContract,
    model = Model(1, "name"),
    metadata = Map.empty
  )
  val mv: ModelVersion.Internal = ModelVersion.Internal(
    id = 10,
    image = DockerImage("name", "tag"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelContract = defaultContract,
    runtime = DockerImage("runtime", "tag"),
    model = Model(1, "name"),
    hostSelector = None,
    status = ModelVersionStatus.Released,
    installCommand = None,
    metadata = Map.empty
  )
  val servable: Servable =
    Servable(mv, "test", Servable.Status.Starting, Nil, "starting", None, None)

  describe("CRUD") {
    it("should not deploy an external ModelVersion") {

      val servableRepo   = mock[ServableRepository[IO]]
      val appRepo        = mock[ApplicationRepository[IO]]
      val monitoringRepo = mock[MonitoringRepository[IO]]

      val cloudDriver = mock[CloudDriver[IO]]
      val monitor     = mock[ServableMonitor[IO]]
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(1)).thenReturn(externalMv.some.pure[IO])

      val service = ServableService[IO](
        nameGen,
        uuidGen,
        cloudDriver,
        servableRepo,
        appRepo,
        versionRepo,
        monitor,
        noopPublisher,
        monitoringRepo
      )
      val deployResult = service.findAndDeploy(1, Map.empty).attempt.unsafeRunSync()
      deployResult.left.value should be(
        DomainError.invalidRequest(
          s"Deployment of external model is unavailable. modelVersionId=${externalMv.id} name=${externalMv.fullName}"
        )
      )
    }

    it("should be able to create Servable") {
      val driverState = ListBuffer.empty[CloudInstance]
      val cloudDriver = mock[CloudDriver[IO]]

      when(cloudDriver.run(any, anyLong, any, any))
        .thenAnswer[String, Long, DockerImage, Option[HostSelector]] {
          (name, modelVersionId, _, _) =>
            IO {
              val instance = CloudInstance(modelVersionId, name, CloudInstance.Status.Starting)
              driverState += instance
              instance
            }
        }

      val repoState    = ListBuffer.empty[Servable]
      val servableRepo = mock[ServableRepository[IO]]
      when(servableRepo.upsert(any)).thenAnswer[Servable](x =>
        IO {
          repoState += x
          x
        }
      )
      when(servableRepo.get(any[String])).thenAnswer[String](name =>
        IO(repoState.find(_.fullName == name))
      )

      val versionRepo  = mock[ModelVersionRepository[IO]]
      val monitorState = ListBuffer.empty[Servable.Status]
      val monitor      = mock[ServableMonitor[IO]]
      when(monitor.monitor(any)).thenAnswer[Servable] { s =>
        logger.info("Monitor triggered")
        monitorState += Servable.Status.Serving
        completedDeferred(s.copy(status = Servable.Status.Serving))
      }
      val events = ListBuffer.empty[Servable]
      val dh     = mock[ServableEvents.Publisher[IO]]
      when(dh.update(any)).thenAnswer[Servable](item => IO(events += item))

      val appRepo = mock[ApplicationRepository[IO]]
      val service = ServableService[IO](
        nameGen,
        uuidGen,
        cloudDriver,
        servableRepo,
        appRepo,
        versionRepo,
        monitor,
        dh,
        null
      )
      val result = service.deploy(mv, Map.empty).unsafeRunSync().completed.get.unsafeRunSync()
      assert(result.modelVersion === mv)
      driverState should not be empty
      monitorState should not be empty
      repoState should not be empty
      println(events)
      events should not be empty
    }

    it("should be able to delete Servable") {
      val mv = ModelVersion.Internal(
        id = 10,
        image = DockerImage("name", "tag"),
        created = Instant.now(),
        finished = None,
        modelVersion = 1,
        modelContract = defaultContract,
        runtime = DockerImage("runtime", "tag"),
        model = Model(1, "test-model"),
        hostSelector = None,
        status = ModelVersionStatus.Assembling,
        installCommand = None,
        metadata = Map.empty
      )

      val initServable = Servable(
        modelVersion = mv,
        nameSuffix = "delete-me",
        Servable.Status.Serving,
        Nil,
        "Ok",
        "host".some,
        9090.some
      )

      val driverState = ListBuffer.empty[String]
      val cloudDriver = mock[CloudDriver[IO]]
      when(cloudDriver.remove(any)).thenAnswer[String](name =>
        IO {
          driverState += name
          ()
        }
      )

      val repoState = ListBuffer.empty[Servable]
      repoState += initServable
      val servableRepo = mock[ServableRepository[IO]]
      when(servableRepo.delete(any[String])).thenAnswer[String](name =>
        OptionT(repoState.find(_.fullName == name).pure[IO])
          .map(x => repoState -= x)
          .map(_ => 1)
          .getOrElseF(IO.raiseError(new Exception(s"SERVABLE $name NOT FOUND")))
      )
      when(servableRepo.get(any[String])).thenAnswer[String](name =>
        IO(repoState.find(_.fullName == name))
      )

      val versionRepo = mock[ModelVersionRepository[IO]]
      val monitor     = mock[ServableMonitor[IO]]

      val events = ListBuffer.empty[String]
      val dh     = mock[ServableEvents.Publisher[IO]]
      when(dh.remove(any)).thenAnswer[String](x => IO(events += x).void)

      val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.findServableUsage(any)).thenReturn(Nil.pure[IO])

      val service = ServableService[IO](
        nameGen,
        uuidGen,
        cloudDriver,
        servableRepo,
        appRepo,
        versionRepo,
        monitor,
        dh,
        null
      )
      val result = service.stop("test-model-1-delete-me").unsafeRunSync()
      assert(result.modelVersion === mv)
      driverState should not be empty
      repoState shouldBe empty
      events should not be empty
    }

    it("should reject deletion of used Servable") {
      val mv = ModelVersion.Internal(
        id = 10,
        image = DockerImage("name", "tag"),
        created = Instant.now(),
        finished = None,
        modelVersion = 1,
        modelContract = defaultContract,
        runtime = DockerImage("runtime", "tag"),
        model = Model(1, "test-model"),
        hostSelector = None,
        status = ModelVersionStatus.Assembling,
        installCommand = None,
        metadata = Map.empty
      )

      val initServable = Servable(
        modelVersion = mv,
        nameSuffix = "delete-me",
        Servable.Status.Serving,
        Nil,
        "Ok",
        "host".some,
        9090.some
      )

      val driverState = ListBuffer.empty[String]
      val cloudDriver = mock[CloudDriver[IO]]
//      when(cloudDriver.remove(any)).thenAnswer[String](name =>
//        IO {
//          driverState += name
//          ()
//        }
//      )

      val repoState = ListBuffer.empty[Servable]
      repoState += initServable
      val servableRepo = mock[ServableRepository[IO]]
      when(servableRepo.get(any[String])).thenAnswer[String](name =>
        IO(repoState.find(_.fullName == name))
      )

      val versionRepo = mock[ModelVersionRepository[IO]]
      val monitor     = mock[ServableMonitor[IO]]

      val events = ListBuffer.empty[Servable]
      val dh     = mock[ServableEvents.Publisher[IO]]
//      when(dh.publish(any)).thenAnswer[DiscoveryEvent[Servable, String]] {
//        case DiscoveryEvent.Initial           => IO.unit
//        case DiscoveryEvent.ItemUpdate(items) => IO(events ++= items)
//        case DiscoveryEvent.ItemRemove(_)     => IO.unit
//      }

      val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.findServableUsage(any[String])).thenReturn(
        IO {
          List(
            Application(
              1,
              "test",
              None,
              Application.Status.Ready,
              Nil,
              null,
              "ok",
              Map.empty
            )
          )
        }
      )

      val service = ServableService[IO](
        nameGen,
        uuidGen,
        cloudDriver,
        servableRepo,
        appRepo,
        versionRepo,
        monitor,
        dh,
        null
      )
      val result = service.stop("test-model-1-delete-me").attempt.unsafeRunSync()
      assert(result.isLeft, result)
    }

    it("should be able to filter servables") {
      val mv = ModelVersion.Internal(
        id = 1,
        image = DockerImage("name", "tag"),
        created = Instant.now(),
        finished = None,
        modelVersion = 1,
        modelContract = defaultContract,
        runtime = DockerImage("runtime", "tag"),
        model = Model(1, "model"),
        hostSelector = None,
        status = ModelVersionStatus.Assembling,
        installCommand = None,
        metadata = Map.empty
      )

      val s1 = Servable(
        modelVersion = mv,
        nameSuffix = "kek",
        status = Servable.Status.Serving,
        usedApps = Nil,
        metadata = Map("author" -> "me", "date" -> "now"),
        message = "OK",
        port = 9090.some,
        host = "host".some
      )
      val s2 = Servable(
        modelVersion = mv,
        nameSuffix = "lol",
        status = Servable.Status.Serving,
        usedApps = Nil,
        metadata = Map("author" -> "you", "date" -> "now"),
        message = "OK",
        host = "host".some,
        port = 9090.some
      )
      val s3 = Servable(
        modelVersion = mv,
        nameSuffix = "foo",
        status = Servable.Status.Serving,
        usedApps = Nil,
        metadata = Map("author" -> "me", "date" -> "yesterday"),
        message = "OK",
        host = "host".some,
        port = 9090.some
      )
      val servableRepo = mock[ServableRepository[IO]]
      when(servableRepo.all()).thenReturn(IO(List(s1, s2, s3)))

      val service = ServableService[IO](
        nameGen,
        uuidGen,
        null,
        servableRepo,
        null,
        null,
        null,
        null,
        null
      )
      val r1 = service.getFiltered(Some("model-1-kek"), None, Map.empty).unsafeRunSync()
      assert(r1 == List(s1))
      val r2 = service.getFiltered(None, Some(1), Map.empty).unsafeRunSync()
      assert(r2 == List(s1, s2, s3))
      val r3 =
        service.getFiltered(None, None, Map("author" -> "me", "date" -> "now")).unsafeRunSync()
      assert(r3 == List(s1))
      val r4 = service
        .getFiltered(Some("model-1-foo"), Some(1), Map("author" -> "me", "date" -> "yesterday"))
        .unsafeRunSync()
      assert(r4 == List(s3))
    }

    it("should be able to deploy ModelVersion") {
      val mv = ModelVersion.Internal(
        id = 1,
        image = DockerImage("name", "tag"),
        created = Instant.now(),
        finished = None,
        modelVersion = 1,
        modelContract = defaultContract,
        runtime = DockerImage("runtime", "tag"),
        model = Model(1, "model"),
        hostSelector = None,
        status = ModelVersionStatus.Released,
        installCommand = None,
        metadata = Map.empty
      )

      val servableMonitor = mock[ServableMonitor[IO]]
      when(servableMonitor.monitor(any)).thenAnswer[Servable](completedDeferred)

      val cd = mock[CloudDriver[IO]]
      when(cd.run(any, any, any, any)).thenAnswer[String, Long, DockerImage, Option[HostSelector]] {
        case (name, modelVersionId, _, _) =>
          IO(CloudInstance(modelVersionId, name, CloudInstance.Status.Running("localhost", 9090)))
      }

      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(anyLong)).thenReturn(mv.some.pure[IO])

      val servableRepo = mock[ServableRepository[IO]]
//      when(servableRepo.all()).thenReturn(Nil.pure[IO])
      when(servableRepo.upsert(any)).thenAnswer[Servable](IO.pure)
      when(servableRepo.get(any[String])).thenReturn(None.pure[IO])

      val service = ServableService[IO](
        nameGen,
        uuidGen,
        cd,
        servableRepo,
        null,
        versionRepo,
        servableMonitor,
        noopPublisher,
        null
      )
      val metadata = Map("author" -> "me", "date" -> "now")
      val result   = service.findAndDeploy(1, metadata).unsafeRunSync()
      assert(result.started.metadata == metadata)
      assert(result.completed.get.unsafeRunSync().metadata == metadata)
    }

    it("should not deploy ModelVersion which is not Released") {
      val mv = ModelVersion.Internal(
        id = 1,
        image = DockerImage("name", "tag"),
        created = Instant.now(),
        finished = None,
        modelVersion = 1,
        modelContract = defaultContract,
        runtime = DockerImage("runtime", "tag"),
        model = Model(1, "model"),
        hostSelector = None,
        status = ModelVersionStatus.Assembling,
        installCommand = None,
        metadata = Map.empty
      )

      val servableMonitor = mock[ServableMonitor[IO]]

      val cd = mock[CloudDriver[IO]]

      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(anyLong)).thenReturn(mv.some.pure[IO])

      val servableRepo = mock[ServableRepository[IO]]

      val service = ServableService[IO](
        nameGen,
        uuidGen,
        cd,
        servableRepo,
        null,
        versionRepo,
        servableMonitor,
        noopPublisher,
        null
      )
      val result = service.findAndDeploy(1, Map.empty).attempt.unsafeRunSync()
      assert(result.isLeft)
    }
  }
}

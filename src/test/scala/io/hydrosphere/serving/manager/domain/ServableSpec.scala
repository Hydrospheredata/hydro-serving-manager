package io.hydrosphere.serving.manager.domain

import java.time.LocalDateTime

import cats.syntax.option._
import cats.data.OptionT
import cats.effect.{IO, Resource}
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable.{GenericServable, OkServable}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableMonitor, ServableRepository, ServableService}
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ServableSpec extends GenericUnitTest {
  implicit val rng = RNG.default[IO].unsafeRunSync()
  implicit val nameGen = NameGenerator.haiku[IO]()
  implicit val timer = IO.timer(ExecutionContext.global)
  val mv = ModelVersion(
    id = 10,
    image = DockerImage("name", "tag"),
    created = LocalDateTime.now(),
    finished = None,
    modelVersion = 1,
    modelContract = ModelContract.defaultInstance,
    runtime = DockerImage("runtime", "tag"),
    model = Model(1, "name"),
    hostSelector = None,
    status = ModelVersionStatus.Assembling,
    profileTypes = Map.empty,
    installCommand = None,
    metadata = Map.empty
  )
  describe("Servable monitoring") {
    it("should succeed on good status and ping") {
      val clientCtor = new PredictionClient.Factory[IO] {
        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
          val clientState = ListBuffer.empty[Unit]
          val client = new PredictionClient[IO] {
            override def status(): IO[StatusResponse] = IO {
              if (clientState.length >= 2) {
                println("Ping - SERVING")
                StatusResponse(
                  status = StatusResponse.ServiceStatus.SERVING,
                  message = "I'm ready"
                )
              } else {
                println("Ping - UNKNOWN")
                clientState += ()
                StatusResponse(
                  status = StatusResponse.ServiceStatus.UNKNOWN,
                  message = "Initializing"
                )
              }
            }
          }
          Resource.liftF(IO(client))
        }
      }
      val driver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???

        val driverState = ListBuffer.empty[Unit]

        override def instance(name: String): IO[Option[CloudInstance]] = name match {
          case "name-1-test" if driverState.length == 2 => IO {
            println("Instance is ready")
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Running("node.cluster.domain", 9090)).some
          }
          case "name-1-test" => IO {
            println("Instance is starting...")
            driverState += ()
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Starting).some
          }
          case _ => IO(None)
        }

        override def run(name: String, modelVersionId: Long, image: DockerImage): IO[CloudInstance] = ???

        override def remove(name: String): IO[Unit] = ???
      }
      val monitor = ServableMonitor.default[IO](clientCtor, driver, 1.second, 10.seconds)
      val result = monitor.monitor("name-1-test").unsafeRunSync()
      assert(result === Servable.Serving("I'm ready", "node.cluster.domain", 9090))
    }

    it("should fail on non-serving status") {
      val clientCtor = new PredictionClient.Factory[IO] {
        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
          val clientState = ListBuffer.empty[Unit]
          val client = new PredictionClient[IO] {
            override def status(): IO[StatusResponse] = IO {
              if (clientState.length >= 2) {
                println("Ping - NOT_SERVING")
                StatusResponse(
                  status = StatusResponse.ServiceStatus.NOT_SERVING,
                  message = "WTF"
                )
              } else {
                println("Ping - UNKNOWN")
                clientState += ()
                StatusResponse(
                  status = StatusResponse.ServiceStatus.UNKNOWN,
                  message = "Initializing"
                )
              }
            }
          }
          Resource.liftF(IO(client))
        }
      }
      val driver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???

        val driverState = ListBuffer.empty[Unit]

        override def instance(name: String): IO[Option[CloudInstance]] = name match {
          case "name-1-test" if driverState.length == 2 => IO {
            println("Instance is ready")
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Running("node.cluster.domain", 9090)).some
          }
          case "name-1-test" => IO {
            println("Instance is starting...")
            driverState += ()
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Starting).some
          }
          case _ => IO(None)
        }

        override def run(name: String, modelVersionId: Long, image: DockerImage): IO[CloudInstance] = ???

        override def remove(name: String): IO[Unit] = ???
      }
      val monitor = ServableMonitor.default[IO](clientCtor, driver, 1.second, 10.seconds)
      val result = monitor.monitor("name-1-test").unsafeRunSync()
      assert(result === Servable.NotServing("WTF", "node.cluster.domain", 9090))
    }

    it("should propagate GRPC errors") {
      val clientCtor = new PredictionClient.Factory[IO] {
        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
          val client = new PredictionClient[IO] {
            override def status(): IO[StatusResponse] = IO.raiseError(new RuntimeException("GRPC test error"))
          }
          Resource.liftF(IO(client))
        }
      }
      val driver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???

        val driverState = ListBuffer.empty[Unit]

        override def instance(name: String): IO[Option[CloudInstance]] = name match {
          case "name-1-test" if driverState.length == 2 => IO {
            println("Instance is ready")
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Running("node.cluster.domain", 9090)).some
          }
          case "name-1-test" => IO {
            println("Instance is starting...")
            driverState += ()
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Starting).some
          }
          case _ => IO(None)
        }

        override def run(name: String, modelVersionId: Long, image: DockerImage): IO[CloudInstance] = ???

        override def remove(name: String): IO[Unit] = ???
      }
      val monitor = ServableMonitor.default[IO](clientCtor, driver, 1.second, 10.seconds)
      val result = monitor.monitor("name-1-test").unsafeRunSync()
      assert(result === Servable.NotAvailable("Ping error: GRPC test error", "node.cluster.domain".some, 9090.some))
    }
  }

  describe("CRUD") {
    it("should be able to create Servable") {

      val driverState = ListBuffer.empty[CloudInstance]
      val cloudDriver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???

        override def instance(name: String): IO[Option[CloudInstance]] = ???

        override def run(name: String, modelVersionId: Long, image: DockerImage): IO[CloudInstance] = {
          val instance = CloudInstance(modelVersionId, name, CloudInstance.Status.Starting)
          driverState += instance
          IO(instance)
        }

        override def remove(name: String): IO[Unit] = ???
      }
      val repoState = ListBuffer.empty[GenericServable]
      val servableRepo = new ServableRepository[IO] {
        override def all(): IO[List[GenericServable]] = IO(repoState.toList)

        override def upsert(servable: GenericServable): IO[GenericServable] = {
          repoState += servable
          IO(servable)
        }

        override def delete(name: String): IO[Int] = ???

        override def get(name: String): IO[Option[GenericServable]] = {
          IO(repoState.find(_.fullName == name))
        }
      }
      val versionRepo = new ModelVersionRepository[IO] {
        override def create(entity: ModelVersion): IO[ModelVersion] = ???

        override def update(id: Long, entity: ModelVersion): IO[Int] = ???

        override def get(id: Long): IO[Option[ModelVersion]] = ???

        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???

        override def get(idx: Seq[Long]): IO[Seq[ModelVersion]] = ???

        override def delete(id: Long): IO[Int] = ???

        override def all(): IO[Seq[ModelVersion]] = ???

        override def listForModel(modelId: Long): IO[Seq[ModelVersion]] = ???

        override def lastModelVersionByModel(modelId: Long, max: Int): IO[Seq[ModelVersion]] = ???
      }
      val monitorState = ListBuffer.empty[Servable.Status]
      val monitor = new ServableMonitor[IO] {
        override def monitor(name: String): IO[Servable.Status] = {
          val servable = Servable.Serving("Ok", "imaginary.host.some-cool-cluster", 6969)
          monitorState += servable
          IO(servable)
        }
      }
      val service = ServableService[IO](cloudDriver, servableRepo, versionRepo, nameGen, monitor)
      val result = service.deploy(mv).unsafeRunSync()
      assert(result.modelVersion === mv)
      driverState should not be empty
      monitorState should not be empty
      repoState should not be empty
    }

    it("should be able to delete Servable") {
      val mv = ModelVersion(
        id = 10,
        image = DockerImage("name", "tag"),
        created = LocalDateTime.now(),
        finished = None,
        modelVersion = 1,
        modelContract = ModelContract.defaultInstance,
        runtime = DockerImage("runtime", "tag"),
        model = Model(1, "test-model"),
        hostSelector = None,
        status = ModelVersionStatus.Assembling,
        profileTypes = Map.empty,
        installCommand = None,
        metadata = Map.empty
      )

      val initServable = Servable(
        modelVersion = mv,
        nameSuffix = "delete-me",
        Servable.Serving("Ok", "host", 9090)
      )

      val driverState = ListBuffer.empty[String]
      val cloudDriver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???

        override def instance(name: String): IO[Option[CloudInstance]] = ???

        override def run(name: String, modelVersionId: Long, image: DockerImage) = ???

        override def remove(name: String): IO[Unit] = {
          driverState += name
          IO.unit
        }
      }
      val repoState = ListBuffer.empty[GenericServable]
      repoState += initServable
      val servableRepo = new ServableRepository[IO] {
        override def all(): IO[List[GenericServable]] = IO(repoState.toList)

        override def upsert(servable: GenericServable): IO[GenericServable] = {
          repoState += servable
          IO(servable)
        }

        override def delete(name: String): IO[Int] = {
          OptionT(get(name))
            .map(x => repoState -= x)
            .map(_ => 1)
            .getOrElseF(IO.raiseError(new Exception(s"SERVABLE $name NOT FOUND")))
        }

        override def get(name: String): IO[Option[GenericServable]] = {
          IO(repoState.find(_.fullName == name))
        }
      }
      val versionRepo = new ModelVersionRepository[IO] {
        override def create(entity: ModelVersion): IO[ModelVersion] = ???

        override def update(id: Long, entity: ModelVersion): IO[Int] = ???

        override def get(id: Long): IO[Option[ModelVersion]] = ???

        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???

        override def get(idx: Seq[Long]): IO[Seq[ModelVersion]] = ???

        override def delete(id: Long): IO[Int] = ???

        override def all(): IO[Seq[ModelVersion]] = ???

        override def listForModel(modelId: Long): IO[Seq[ModelVersion]] = ???

        override def lastModelVersionByModel(modelId: Long, max: Int): IO[Seq[ModelVersion]] = ???
      }
      val monitor = new ServableMonitor[IO] {
        override def monitor(name: String) = ???
      }
      val service = ServableService[IO](cloudDriver, servableRepo, versionRepo, nameGen, monitor)
      val result = service.stop("test-model-1-delete-me").unsafeRunSync()
      assert(result.modelVersion === mv)
      driverState should not be empty
      repoState shouldBe empty
    }
  }
}
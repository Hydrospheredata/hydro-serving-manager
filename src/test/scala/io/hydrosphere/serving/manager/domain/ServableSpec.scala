package io.hydrosphere.serving.manager.domain

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

import akka.stream.scaladsl.Source
import cats.data.OptionT
import cats.effect.concurrent.Deferred
import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import fs2.concurrent.Queue
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.{GenericUnitTest, db}
import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.discovery.{DiscoveryEvent, ServablePublisher}
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.ServableMonitor.MonitoringEntry
import io.hydrosphere.serving.manager.domain.servable._
import io.hydrosphere.serving.manager.grpc.entities
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ServableSpec extends GenericUnitTest {
  implicit val rng: RNG[IO] = RNG.default[IO].unsafeRunSync()
  implicit val nameGen: NameGenerator[IO] = NameGenerator.haiku[IO]()
  implicit val uuidGen = UUIDGenerator.default[IO]()
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
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
  val servable = Servable(mv, "test", Servable.Starting("Init", None, None))

  describe("Servable probe") {
    it("should succed on good status and ping") {
      implicit val driver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???
        override def instance(name: String): IO[Option[CloudInstance]] = name match {
          case "name-1-test" => IO {
            println("Instance is ready")
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Running("node.cluster.domain", 9090)).some
          }
          case _ => IO(None)
        }
        override def run(name: String, modelVersionId: Long, image: DockerImage, hostSelector: Option[HostSelector] = None): IO[CloudInstance] = ???
        override def remove(name: String): IO[Unit] = ???

        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???

        override def getLogs(name: String, follow: Boolean): IO[Source[String, _]] = ???
      }
      implicit val clientCtor = new PredictionClient.Factory[IO] {
        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
          val client = new PredictionClient[IO] {
            override def status(): IO[StatusResponse] = IO {
              println("Ping - SERVING")
              StatusResponse(
                status = StatusResponse.ServiceStatus.SERVING,
                message = "Ok"
              )
            }
          }
          Resource.liftF(IO(client))
        }
      }
      val probe = ServableProbe.default[IO]()
      val res = probe.probe(servable).unsafeRunSync()
      assert(res.isInstanceOf[Servable.Serving])
    }

    it("should fail on non-serving status") {
      implicit val clientCtor = new PredictionClient.Factory[IO] {
        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
          val client = new PredictionClient[IO] {
            override def status(): IO[StatusResponse] = IO {
              println("Ping - NOT_SERVING")
              StatusResponse(
                status = StatusResponse.ServiceStatus.NOT_SERVING,
                message = "WTF"
              )
            }
          }
          Resource.liftF(IO(client))
        }
      }
      implicit val driver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???
        override def instance(name: String): IO[Option[CloudInstance]] = name match {
          case "name-1-test" => IO {
            println("Instance is ready")
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Running("node.cluster.domain", 9090)).some
          }
          case _ => IO(None)
        }
        override def run(name: String, modelVersionId: Long, image: DockerImage, hostSelector: Option[HostSelector] = None): IO[CloudInstance] = ???
        override def remove(name: String): IO[Unit] = ???

        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???

        override def getLogs(name: String, follow: Boolean): IO[Source[String, _]] = ???
      }
      val probe = ServableProbe.default[IO]()
      val result = probe.probe(servable).unsafeRunSync()
      assert(result.isInstanceOf[Servable.NotServing])
    }

    it("should propagate GRPC errors") {
      implicit val clientCtor = new PredictionClient.Factory[IO] {
        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
          val client = new PredictionClient[IO] {
            override def status(): IO[StatusResponse] = IO.raiseError(new RuntimeException("GRPC test error"))
          }
          Resource.liftF(IO(client))
        }
      }
      implicit val driver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???
        override def instance(name: String): IO[Option[CloudInstance]] = name match {
          case "name-1-test" => IO {
            println("Instance is ready")
            CloudInstance(mv.id, Servable.fullName(mv.model.name, mv.modelVersion, "test"), CloudInstance.Status.Running("node.cluster.domain", 9090)).some
          }
          case _ => IO(None)
        }
        override def run(name: String, modelVersionId: Long, image: DockerImage, hostSelector: Option[HostSelector] = None): IO[CloudInstance] = ???
        override def remove(name: String): IO[Unit] = ???

        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???

        override def getLogs(name: String, follow: Boolean): IO[Source[String, _]] = ???
      }
      val probe = ServableProbe.default[IO]()
      val result = probe.probe(servable).unsafeRunSync()
      assert(result === Servable.NotAvailable("Ping error: GRPC test error", "node.cluster.domain".some, 9090.some))
    }
  }

  describe("Servable monitoring queue") {
    it("should handle Serving status") {
      implicit val probe = new ServableProbe[IO] {
        override def probe(servable: GenericServable): IO[Servable.Status] =
          IO(Servable.Serving("Ok", "host", 9090))
      }
      var res = Option.empty[GenericServable]
      implicit val repo = new ServableRepository[IO] {
        override def all(): IO[List[GenericServable]] = ???
        override def upsert(servable: GenericServable): IO[GenericServable] = IO {
          res = servable.some
          servable
        }
        override def delete(name: String): IO[Int] = ???
        override def get(name: String): IO[Option[GenericServable]] = ???

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
      }
      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      println("Created queue")
      val cancellableMonitor = ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
      println("Created Monitor")
      val fbr = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d = monitor.monitor(servable).unsafeRunSync()
      println("Submitted servable")
      val result = d.get.unsafeRunSync()
      println("Got result")
      fbr.cancel.unsafeRunSync()
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      assert(result.status.isInstanceOf[Servable.Serving])
      assert(res.get === result)
    }

    it("should wait Starting status to get to the final state") {
      implicit val probe = new ServableProbe[IO] {
        private val probeState = new AtomicInteger(0)
        override def probe(servable: GenericServable): IO[Servable.Status] =
          probeState match {
            case x if x.getAndIncrement() < 10 => IO(Servable.Starting("Wait pls", None, None))
            case _ => IO(Servable.Serving("Ok", "host", 9090))
          }
      }
      var res = Option.empty[GenericServable]
      implicit val repo = new ServableRepository[IO] {
        override def all(): IO[List[GenericServable]] = ???
        override def upsert(servable: GenericServable): IO[GenericServable] = IO {
          res = servable.some
          servable
        }
        override def delete(name: String): IO[Int] = ???
        override def get(name: String): IO[Option[GenericServable]] = ???

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
      }
      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      println("Created queue")
      val cancellableMonitor = ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
      println("Created Monitor")
      val fbr = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d = monitor.monitor(servable).unsafeRunSync()
      println("Submitted servable")
      val result = d.get.unsafeRunSync()
      println("Got result")
      fbr.cancel.unsafeRunSync()
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      assert(result.status.isInstanceOf[Servable.Serving])
      assert(res.get === result)
    }

    it("should set NotServable after NotAvailable status probed several times") {
      implicit val probe = new ServableProbe[IO] {
        override def probe(servable: GenericServable): IO[Servable.Status] =
          IO(Servable.NotAvailable("not available yet", None, None))
      }
      var res = Option.empty[GenericServable]
      implicit val repo = new ServableRepository[IO] {
        override def all(): IO[List[GenericServable]] = ???
        override def upsert(servable: GenericServable): IO[GenericServable] = IO {
          res = servable.some
          servable
        }
        override def delete(name: String): IO[Int] = ???
        override def get(name: String): IO[Option[GenericServable]] = ???

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
      }
      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      println("Created queue")
      val cancellableMonitor = ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
      println("Created Monitor")
      val fbr = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d = monitor.monitor(servable).unsafeRunSync()
      println("Submitted servable")
      val result = d.get.unsafeRunSync()
      println("Got result")
      fbr.cancel.unsafeRunSync()
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      assert(result.status.isInstanceOf[Servable.NotServing])
      assert(res.get === result)
    }

    it("should set NotAvailable status on probe error") {
      implicit val probe = new ServableProbe[IO] {
        override def probe(servable: GenericServable): IO[Servable.Status] = IO.raiseError(new Exception("OOPSIE"))
      }
      var res = Option.empty[GenericServable]
      implicit val repo = new ServableRepository[IO] {
        override def all(): IO[List[GenericServable]] = ???
        override def upsert(servable: GenericServable): IO[GenericServable] = IO {
          res = servable.some
          servable
        }
        override def delete(name: String): IO[Int] = ???
        override def get(name: String): IO[Option[GenericServable]] = ???

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
      }
      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      println("Created queue")
      val cancellableMonitor = ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
      println("Created Monitor")
      val fbr = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d = monitor.monitor(servable).unsafeRunSync()
      println("Submitted servable")
      val result = d.get.unsafeRunSync()
      println("Got result")
      fbr.cancel.unsafeRunSync()
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      assert(result.status.isInstanceOf[Servable.NotServing])
      assert(res.get === result)
    }
  }

  describe("CRUD") {
    it("should be able to create Servable") {

      val driverState = ListBuffer.empty[CloudInstance]
      val cloudDriver = new CloudDriver[IO] {
        override def instances: IO[List[CloudInstance]] = ???

        override def instance(name: String): IO[Option[CloudInstance]] = ???

        override def run(name: String, modelVersionId: Long, image: DockerImage, hostSelector: Option[HostSelector] = None): IO[CloudInstance] = {
          val instance = CloudInstance(modelVersionId, name, CloudInstance.Status.Starting)
          driverState += instance
          IO(instance)
        }

        override def remove(name: String): IO[Unit] = ???

        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???

        override def getLogs(name: String, follow: Boolean): IO[Source[String, _]] = ???
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

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
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
        override def monitor(s: GenericServable): IO[Deferred[IO, GenericServable]] = {
          val servable = Servable.Serving("Ok", "imaginary.host.some-cool-cluster", 6969)
          monitorState += servable
          val d = Deferred.unsafe[IO, GenericServable]
          d.complete(s.copy(status = servable)).unsafeRunSync()
          IO(d)
        }
      }
      val events = ListBuffer.empty[GenericServable]
      val dh = new ServablePublisher[IO] {
        override def update(item: GenericServable): IO[Unit] = IO(events += item)

        override def remove(itemId: String): IO[Unit] = IO.unit

        override def publish(t: DiscoveryEvent[GenericServable, String]): IO[Unit] = {
          t match {
            case DiscoveryEvent.Initial => IO.unit
            case DiscoveryEvent.ItemUpdate(items) => IO(events ++= items)
            case DiscoveryEvent.ItemRemove(items) => IO.unit
          }
        }
      }
      val appRepo = new ApplicationRepository[IO] {
        override def create(entity: GenericApplication): IO[GenericApplication] = ???
        override def get(id: Long): IO[Option[GenericApplication]] = ???
        override def get(name: String): IO[Option[GenericApplication]] = ???
        override def update(value: GenericApplication): IO[Int] = ???
        override def updateRow(row: Tables.ApplicationRow): IO[Int] = ???
        override def delete(id: Long): IO[Int] = ???
        override def all(): IO[List[GenericApplication]] = ???
        override def applicationsWithCommonServices(servables: Set[GenericServable], applicationId: Long): IO[List[GenericApplication]] = ???
        override def findVersionsUsage(versionIdx: Long): IO[List[GenericApplication]] = ???
        override def findServableUsage(servableName: String): IO[List[GenericApplication]] = ???
      }
      val service = ServableService[IO](cloudDriver, servableRepo, appRepo, versionRepo, monitor, dh)
      val result = service.deploy(mv).unsafeRunSync().completed.get.unsafeRunSync()
      assert(result.modelVersion === mv)
      driverState should not be empty
      monitorState should not be empty
      repoState should not be empty
      println(events)
      events should not be empty
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

        override def run(name: String, modelVersionId: Long, image: DockerImage, hostSelector: Option[HostSelector] = None) = ???

        override def remove(name: String): IO[Unit] = {
          driverState += name
          IO.unit
        }

        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???

        override def getLogs(name: String, follow: Boolean): IO[Source[String, _]] = ???
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

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
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
        override def monitor(name: GenericServable) = ???
      }

      val events = ListBuffer.empty[GenericServable]
      val dh = new ServablePublisher[IO] {
        override def publish(t: DiscoveryEvent[GenericServable, String]): IO[Unit] = {
          t match {
            case DiscoveryEvent.Initial => IO.unit
            case DiscoveryEvent.ItemUpdate(items) => IO(events ++= items)
            case DiscoveryEvent.ItemRemove(items) => IO.unit
          }
        }
      }
      val appRepo = new ApplicationRepository[IO] {
        override def create(entity: GenericApplication): IO[GenericApplication] = ???
        override def get(id: Long): IO[Option[GenericApplication]] = ???
        override def get(name: String): IO[Option[GenericApplication]] = ???
        override def update(value: GenericApplication): IO[Int] = ???
        override def updateRow(row: db.Tables.ApplicationRow): IO[Int] = ???
        override def delete(id: Long): IO[Int] = ???
        override def all(): IO[List[GenericApplication]] = ???
        override def applicationsWithCommonServices(servables: Set[GenericServable], applicationId: Long): IO[List[GenericApplication]] = ???
        override def findVersionsUsage(versionIdx: Long): IO[List[GenericApplication]] = ???
        override def findServableUsage(servableName: String): IO[List[GenericApplication]] = IO(Nil)
      }
      val service = ServableService[IO](cloudDriver, servableRepo, appRepo, versionRepo, monitor, dh)
      val result = service.stop("test-model-1-delete-me").unsafeRunSync()
      assert(result.modelVersion === mv)
      driverState should not be empty
      repoState shouldBe empty
    }


    it("should reject deletion of used Servable") {
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

        override def run(name: String, modelVersionId: Long, image: DockerImage, hostSelector: Option[HostSelector] = None) = ???

        override def remove(name: String): IO[Unit] = {
          driverState += name
          IO.unit
        }

        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???

        override def getLogs(name: String, follow: Boolean): IO[Source[String, _]] = ???
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

        override def get(names: Seq[String]): IO[List[GenericServable]] = ???
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
        override def monitor(name: GenericServable) = ???
      }

      val events = ListBuffer.empty[GenericServable]
      val dh = new ServablePublisher[IO] {
        override def publish(t: DiscoveryEvent[GenericServable, String]): IO[Unit] = {
          t match {
            case DiscoveryEvent.Initial => IO.unit
            case DiscoveryEvent.ItemUpdate(items) => IO(events ++= items)
            case DiscoveryEvent.ItemRemove(items) => IO.unit
          }
        }
      }
      val appRepo = new ApplicationRepository[IO] {
        override def create(entity: GenericApplication): IO[GenericApplication] = ???
        override def get(id: Long): IO[Option[GenericApplication]] = ???
        override def get(name: String): IO[Option[GenericApplication]] = ???
        override def update(value: GenericApplication): IO[Int] = ???
        override def updateRow(row: db.Tables.ApplicationRow): IO[Int] = ???
        override def delete(id: Long): IO[Int] = ???
        override def all(): IO[List[GenericApplication]] = ???
        override def applicationsWithCommonServices(servables: Set[GenericServable], applicationId: Long): IO[List[GenericApplication]] = ???
        override def findVersionsUsage(versionIdx: Long): IO[List[GenericApplication]] = ???
        override def findServableUsage(servableName: String): IO[List[GenericApplication]] = IO{
          List(Application(1, "test", None, Application.Ready(null), ModelSignature.defaultInstance, Nil))
        }
      }
      val service = ServableService[IO](cloudDriver, servableRepo, appRepo, versionRepo, monitor, dh)
      val result = service.stop("test-model-1-delete-me").attempt.unsafeRunSync()
      assert(result.isLeft, result)
    }
  }
}
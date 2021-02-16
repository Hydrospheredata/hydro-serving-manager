package io.hydrosphere.serving.manager.domain

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import akka.stream.scaladsl.Source
import cats.data.OptionT
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, IO, Resource, Timer}
import cats.implicits._
import fs2.concurrent.Queue

//import io.hydrosphere.serving.contract.model_contract.ModelContract
//import io.hydrosphere.serving.contract.model_signature.ModelSignature

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfiguration,
  DeploymentConfigurationService
}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring.{
  CustomModelMetricSpec,
  MonitoringRepository
}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.ServableMonitor.MonitoringEntry
import io.hydrosphere.serving.manager.domain.servable._
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// TODO

class ServableSpec extends GenericUnitTest {
  implicit val rng: RNG[IO]               = RNG.default[IO].unsafeRunSync()
  implicit val nameGen: NameGenerator[IO] = NameGenerator.haiku[IO]()
  implicit val uuidGen: UUIDGenerator[IO] = UUIDGenerator.default[IO]()
  implicit val timer: Timer[IO]           = IO.timer(ExecutionContext.global)

//  val externalMv = ModelVersion.External(
//    id = 1,
//    created = Instant.now(),
//    modelVersion = 1,
//    modelContract = ModelContract.defaultInstance,
//    model = Model(1, "name"),
//    metadata = Map.empty
//  )
//  val mv = ModelVersion.Internal(
//    id = 10,
//    image = DockerImage("name", "tag"),
//    created = Instant.now(),
//    finished = None,
//    modelVersion = 1,
//    modelContract = ModelContract.defaultInstance,
//    runtime = DockerImage("runtime", "tag"),
//    model = Model(1, "name"),
//    status = ModelVersionStatus.Released,
//    installCommand = None,
//    metadata = Map.empty
//  )
//  val servable = Servable(mv, "test", Servable.Starting("Init", None, None), Nil)

//  describe("Servable probe") {
//    it("should succed on good status and ping") {
//      implicit val driver = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]] = ???
//        override def instance(name: String): IO[Option[CloudInstance]] =
//          name match {
//            case "name-1-test" =>
//              IO {
//                println("Instance is ready")
//                CloudInstance(
//                  mv.id,
//                  Servable.fullName(mv.model.name, mv.modelVersion, "test"),
//                  CloudInstance.Status.Running("node.cluster.domain", 9090)
//                ).some
//              }
//            case _ => IO(None)
//          }
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration] = None
//        ): IO[CloudInstance]                        = ???
//        override def remove(name: String): IO[Unit] = ???
//
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String] = ???
//      }
//      implicit val clientCtor = new PredictionClient.Factory[IO] {
//        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
//          val client = new PredictionClient[IO] {
//            override def status(): IO[StatusResponse] =
//              IO {
//                println("Ping - SERVING")
//                StatusResponse(
//                  status = StatusResponse.ServiceStatus.SERVING,
//                  message = "Ok"
//                )
//              }
//          }
//          Resource.liftF(IO(client))
//        }
//      }
//      val probe = ServableProbe.default[IO]()
//      val res   = probe.probe(servable).unsafeRunSync()
//      assert(res.isInstanceOf[Servable.Serving])
//    }
//
//    it("should fail on non-serving status") {
//      implicit val clientCtor = new PredictionClient.Factory[IO] {
//        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
//          val client = new PredictionClient[IO] {
//            override def status(): IO[StatusResponse] =
//              IO {
//                println("Ping - NOT_SERVING")
//                StatusResponse(
//                  status = StatusResponse.ServiceStatus.NOT_SERVING,
//                  message = "WTF"
//                )
//              }
//          }
//          Resource.liftF(IO(client))
//        }
//      }
//      implicit val driver = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]] = ???
//        override def instance(name: String): IO[Option[CloudInstance]] =
//          name match {
//            case "name-1-test" =>
//              IO {
//                println("Instance is ready")
//                CloudInstance(
//                  mv.id,
//                  Servable.fullName(mv.model.name, mv.modelVersion, "test"),
//                  CloudInstance.Status.Running("node.cluster.domain", 9090)
//                ).some
//              }
//            case _ => IO(None)
//          }
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration] = None
//        ): IO[CloudInstance]                                                         = ???
//        override def remove(name: String): IO[Unit]                                  = ???
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String]  = ???
//      }
//      val probe  = ServableProbe.default[IO]()
//      val result = probe.probe(servable).unsafeRunSync()
//      assert(result.isInstanceOf[Servable.NotServing])
//    }
//
//    it("should propagate GRPC errors") {
//      implicit val clientCtor = new PredictionClient.Factory[IO] {
//        override def make(host: String, port: Int): Resource[IO, PredictionClient[IO]] = {
//          val client = new PredictionClient[IO] {
//            override def status(): IO[StatusResponse] =
//              IO.raiseError(new RuntimeException("GRPC test error"))
//          }
//          Resource.liftF(IO(client))
//        }
//      }
//      implicit val driver = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]] = ???
//        override def instance(name: String): IO[Option[CloudInstance]] =
//          name match {
//            case "name-1-test" =>
//              IO {
//                println("Instance is ready")
//                CloudInstance(
//                  mv.id,
//                  Servable.fullName(mv.model.name, mv.modelVersion, "test"),
//                  CloudInstance.Status.Running("node.cluster.domain", 9090)
//                ).some
//              }
//            case _ => IO(None)
//          }
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration] = None
//        ): IO[CloudInstance]                                                         = ???
//        override def remove(name: String): IO[Unit]                                  = ???
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String]  = ???
//      }
//      val probe  = ServableProbe.default[IO]()
//      val result = probe.probe(servable).unsafeRunSync()
//      assert(
//        result === Servable
//          .NotAvailable("Ping error: GRPC test error", "node.cluster.domain".some, 9090.some)
//      )
//    }
//  }

//  describe("Servable monitoring queue") {
//    it("should handle Serving status") {
//      implicit val probe = new ServableProbe[IO] {
//        override def probe(servable: Servable): IO[Servable.Status] =
//          IO(Servable.Serving("Ok", "host", 9090))
//      }
//      var res = Option.empty[Servable]
//      implicit val repo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]] = ???
//        override def upsert(servable: Servable): IO[Servable] =
//          IO {
//            res = servable.some
//            servable
//          }
//        override def delete(name: String): IO[Int]                            = ???
//        override def get(name: String): IO[Option[Servable]]                  = ???
//        override def get(names: Seq[String]): IO[List[Servable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[Servable]] = ???
//      }
//      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
//      println("Created queue")
//      val cancellableMonitor =
//        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
//      println("Created Monitor")
//      val fbr     = cancellableMonitor.fiber
//      val monitor = cancellableMonitor.mon
//      val d       = monitor.monitor(servable).unsafeRunSync()
//      println("Submitted servable")
//      val result = d.get.unsafeRunSync()
//      println("Got result")
//      fbr.cancel.unsafeRunSync()
//      assert(queue.tryDequeue1.unsafeRunSync() === None)
//      assert(result.status.isInstanceOf[Servable.Serving])
//      assert(res.get === result)
//    }
//
//    it("should wait Starting status to get to the final state") {
//      implicit val probe = new ServableProbe[IO] {
//        private val probeState = new AtomicInteger(0)
//
//        override def probe(servable: Servable): IO[Servable.Status] =
//          probeState match {
//            case x if x.getAndIncrement() < 10 => IO(Servable.Starting("Wait pls", None, None))
//            case _                             => IO(Servable.Serving("Ok", "host", 9090))
//          }
//      }
//      var res = Option.empty[Servable]
//      implicit val repo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]] = ???
//        override def upsert(servable: Servable): IO[Servable] =
//          IO {
//            res = servable.some
//            servable
//          }
//        override def delete(name: String): IO[Int]                            = ???
//        override def get(name: String): IO[Option[Servable]]                  = ???
//        override def get(names: Seq[String]): IO[List[Servable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[Servable]] = ???
//      }
//      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
//      println("Created queue")
//      val cancellableMonitor =
//        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
//      println("Created Monitor")
//      val fbr     = cancellableMonitor.fiber
//      val monitor = cancellableMonitor.mon
//      val d       = monitor.monitor(servable).unsafeRunSync()
//      println("Submitted servable")
//      val result = d.get.unsafeRunSync()
//      println("Got result")
//      fbr.cancel.unsafeRunSync()
//      assert(queue.tryDequeue1.unsafeRunSync() === None)
//      assert(result.status.isInstanceOf[Servable.Serving])
//      assert(res.get === result)
//    }
//
//    it("should set NotServable after NotAvailable status probed several times") {
//      implicit val probe = new ServableProbe[IO] {
//        override def probe(servable: Servable): IO[Servable.Status] =
//          IO(Servable.NotAvailable("not available yet", None, None))
//      }
//      var res = Option.empty[Servable]
//      implicit val repo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]] = ???
//        override def upsert(servable: Servable): IO[Servable] =
//          IO {
//            res = servable.some
//            servable
//          }
//        override def delete(name: String): IO[Int]                            = ???
//        override def get(name: String): IO[Option[Servable]]                  = ???
//        override def get(names: Seq[String]): IO[List[Servable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[Servable]] = ???
//      }
//      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
//      println("Created queue")
//      val cancellableMonitor =
//        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
//      println("Created Monitor")
//      val fbr     = cancellableMonitor.fiber
//      val monitor = cancellableMonitor.mon
//      val d       = monitor.monitor(servable).unsafeRunSync()
//      println("Submitted servable")
//      val result = d.get.unsafeRunSync()
//      println("Got result")
//      fbr.cancel.unsafeRunSync()
//      assert(queue.tryDequeue1.unsafeRunSync() === None)
//      assert(result.status.isInstanceOf[Servable.NotServing])
//      assert(res.get === result)
//    }
//
//    it("should set NotAvailable status on probe error") {
//      implicit val probe = new ServableProbe[IO] {
//        override def probe(servable: Servable): IO[Servable.Status] =
//          IO.raiseError(new Exception("OOPSIE"))
//      }
//      var res = Option.empty[Servable]
//      implicit val repo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]] = ???
//        override def upsert(servable: Servable): IO[Servable] =
//          IO {
//            res = servable.some
//            servable
//          }
//        override def delete(name: String): IO[Int]                            = ???
//        override def get(name: String): IO[Option[Servable]]                  = ???
//        override def get(names: Seq[String]): IO[List[Servable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[Servable]] = ???
//      }
//      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
//      println("Created queue")
//      val cancellableMonitor =
//        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds).unsafeRunSync()
//      println("Created Monitor")
//      val fbr     = cancellableMonitor.fiber
//      val monitor = cancellableMonitor.mon
//      val d       = monitor.monitor(servable).unsafeRunSync()
//      println("Submitted servable")
//      val result = d.get.unsafeRunSync()
//      println("Got result")
//      fbr.cancel.unsafeRunSync()
//      assert(queue.tryDequeue1.unsafeRunSync() === None)
//      assert(result.status.isInstanceOf[Servable.NotServing])
//      assert(res.get === result)
//    }
//  }

//  describe("CRUD") {
//    def depConfService =
//      new DeploymentConfigurationService[IO] {
//        override def all(): IO[List[DeploymentConfiguration]] = ???
//        override def create(
//            deploymentConfiguration: DeploymentConfiguration
//        ): IO[DeploymentConfiguration]                                 = ???
//        override def delete(name: String): IO[DeploymentConfiguration] = ???
//        override def get(name: String): IO[DeploymentConfiguration]    = ???
//      }
//
//    it("should not deploy an external ModelVersion") {
//      val cloudDriver = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]]                = ???
//        override def instance(name: String): IO[Option[CloudInstance]] = ???
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration] = None
//        ): IO[CloudInstance] =
//          IO.raiseError(new IllegalStateException("Shouldn't reach this"))
//        override def remove(name: String): IO[Unit]                                  = ???
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String]  = ???
//      }
//      val servableRepo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]]                                = ???
//        override def upsert(servable: Servable): IO[Servable]                 = ???
//        override def delete(name: String): IO[Int]                            = ???
//        override def get(name: String): IO[Option[Servable]]                  = ???
//        override def get(names: Seq[String]): IO[List[Servable]]              = ???
//        override def findForModelVersion(versionId: Long): IO[List[Servable]] = ???
//      }
//      val versionRepo = new ModelVersionRepository[IO] {
//        override def create(entity: ModelVersion): IO[ModelVersion]                       = ???
//        override def get(id: Long): IO[Option[ModelVersion]]                              = IO(Some(externalMv))
//        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
//        override def delete(id: Long): IO[Int]                                            = ???
//        override def all(): IO[List[ModelVersion]]                                        = ???
//        override def listForModel(modelId: Long): IO[List[ModelVersion]]                  = ???
//        override def update(entity: ModelVersion): IO[Int]                                = ???
//        override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]]     = ???
//      }
//      val monitor = new ServableMonitor[IO] {
//        override def monitor(s: Servable): IO[Deferred[IO, Servable]] =
//          IO.raiseError(new IllegalStateException("Shouldn't reach this"))
//      }
//      val appRepo = new ApplicationRepository[IO] {
//        override def create(entity: Application): IO[Application]                   = ???
//        override def get(id: Long): IO[Option[Application]]                         = ???
//        override def get(name: String): IO[Option[Application]]                     = ???
//        override def update(value: Application): IO[Int]                            = ???
//        override def delete(id: Long): IO[Int]                                      = ???
//        override def all(): IO[List[Application]]                                   = ???
//        override def findVersionUsage(versionIdx: Long): IO[List[Application]]      = ???
//        override def findServableUsage(servableName: String): IO[List[Application]] = ???
//      }
//
//      val monitoringRepo = new MonitoringRepository[IO] {
//        override def all(): IO[List[CustomModelMetricSpec]]                     = ???
//        override def get(id: String): IO[Option[CustomModelMetricSpec]]         = ???
//        override def forModelVersion(id: Long): IO[List[CustomModelMetricSpec]] = ???
//        override def upsert(spec: CustomModelMetricSpec): IO[Unit]              = ???
//        override def delete(id: String): IO[Unit]                               = ???
//      }
//
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
//
//      val driverState = ListBuffer.empty[CloudInstance]
//      val cloudDriver = new CloudDriver[IO] {
//        override def instances: IO[List[CloudInstance]]                = ???
//        override def instance(name: String): IO[Option[CloudInstance]] = ???
//        override def run(
//            name: String,
//            modelVersionId: Long,
//            image: DockerImage,
//            hostSelector: Option[DeploymentConfiguration] = None
//        ): IO[CloudInstance] = {
//          val instance = CloudInstance(modelVersionId, name, CloudInstance.Status.Starting)
//          driverState += instance
//          IO(instance)
//        }
//        override def remove(name: String): IO[Unit]                                  = ???
//        override def getByVersionId(modelVersionId: Long): IO[Option[CloudInstance]] = ???
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String]  = ???
//      }
//      val repoState = ListBuffer.empty[Servable]
//      val servableRepo = new ServableRepository[IO] {
//        override def all(): IO[List[Servable]] = IO(repoState.toList)
//        override def upsert(servable: Servable): IO[Servable] = {
//          repoState += servable
//          IO(servable)
//        }
//        override def delete(name: String): IO[Int] = ???
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
//      val monitorState = ListBuffer.empty[Servable.Status]
//      val monitor = new ServableMonitor[IO] {
//        override def monitor(s: Servable): IO[Deferred[IO, Servable]] = {
//          val servable = Servable.Serving("Ok", "imaginary.host.some-cool-cluster", 6969)
//          monitorState += servable
//          val d = Deferred.unsafe[IO, Servable]
//          d.complete(s.copy(status = servable)).unsafeRunSync()
//          IO(d)
//        }
//      }
//      val appRepo = new ApplicationRepository[IO] {
//        override def create(entity: Application): IO[Application]                   = ???
//        override def get(id: Long): IO[Option[Application]]                         = ???
//        override def get(name: String): IO[Option[Application]]                     = ???
//        override def update(value: Application): IO[Int]                            = ???
//        override def delete(id: Long): IO[Int]                                      = ???
//        override def all(): IO[List[Application]]                                   = ???
//        override def findVersionUsage(versionIdx: Long): IO[List[Application]]      = ???
//        override def findServableUsage(servableName: String): IO[List[Application]] = ???
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
//      val result = service.deploy(mv, None, Map.empty).unsafeRunSync().completed.get.unsafeRunSync()
//      assert(result.modelVersion === mv)
//      driverState should not be empty
//      monitorState should not be empty
//      repoState should not be empty
//    }
//
//    it("should be able to delete Servable") {
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
//        override def getLogs(name: String, follow: Boolean): fs2.Stream[IO, String]  = ???
//      }
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
}

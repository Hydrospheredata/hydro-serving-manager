package io.hydrosphere.serving.manager.domain.servable

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO
import cats.implicits._
import cats.effect.concurrent.Deferred
import fs2.concurrent.Queue
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.ServableMonitor.MonitoringEntry

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class ServableMonitoringSpec extends GenericUnitTest {
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

  def mockServableRepoUpsert(ref: Deferred[IO, Servable]): ServableRepository[IO] = {
    val repo: ServableRepository[IO] = mock[ServableRepository[IO]]
    when(repo.upsert(any)).thenAnswer[Servable](s => ref.complete(s).as(s))
    repo
  }

  describe("Servable monitoring queue") {
    it("should handle Serving status") {
      val probe: ServableProbe[IO] = mock[ServableProbe[IO]]
      when(probe.probe(any)).thenReturn(IO(ProbeResponse.Ok("host", 9090, "ok")))

      val res                          = Deferred.unsafe[IO, Servable]
      val repo: ServableRepository[IO] = mockServableRepoUpsert(res)

      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      logger.info(s"Created ${queue}")
      val cancellableMonitor =
        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds, probe, repo).unsafeRunSync()
      logger.info(s"Created Monitor ${cancellableMonitor}")
      val fbr     = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d       = monitor.monitor(servable).unsafeRunSync()
      logger.info(s"Submitted ${servable}")
      val result = d.get.unsafeRunSync()
      logger.info(s"Got result ${result}")
      fbr.cancel.unsafeRunSync()
      logger.info(s"Cancelled fiber")
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      logger.info(s"Q is empty")
      assert(result.status === Servable.Status.Serving)
      logger.info(s"Servable status ${result.status}")
      val pRes = res.get.unsafeRunSync()
      logger.info(s"Deferred result ${pRes}")
      assert(pRes === result)
    }

    it("should wait Starting status to get to the final state") {
      val probeState               = new AtomicInteger(0)
      val probe: ServableProbe[IO] = mock[ServableProbe[IO]]
      when(probe.probe(any)).thenAnswer[Servable] { s =>
        IO {
          probeState match {
            case x if x.getAndIncrement() < 10 =>
              ProbeResponse.PingError(
                status = Servable.Status.Starting,
                host = "host",
                port = 9090,
                message = "wait"
              )
            case _ =>
              ProbeResponse.Ok(
                host = "host",
                port = 9090,
                message = "ok"
              )
          }
        }
      }
      val res                          = Deferred.unsafe[IO, Servable]
      val repo: ServableRepository[IO] = mockServableRepoUpsert(res)

      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      logger.info("Created queue")
      val cancellableMonitor =
        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds, probe, repo).unsafeRunSync()
      logger.info("Created Monitor")
      val fbr     = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d       = monitor.monitor(servable).unsafeRunSync()
      logger.info("Submitted servable")
      val result = d.get.unsafeRunSync()
      logger.info("Got result")
      fbr.cancel.unsafeRunSync()
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      assert(result.status === Servable.Status.Serving)
      assert(res.get.unsafeRunSync() === result)
    }

    it("should set NotServable after NotAvailable status probed several times") {
      val probe = mock[ServableProbe[IO]]
      when(probe.probe(any)).thenReturn(IO(ProbeResponse.InstanceNotFound("not available yet")))

      val res                          = Deferred.unsafe[IO, Servable]
      val repo: ServableRepository[IO] = mockServableRepoUpsert(res)

      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      logger.info("Created queue")
      val cancellableMonitor =
        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds, probe, repo).unsafeRunSync()
      logger.info("Created Monitor")
      val fbr     = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d       = monitor.monitor(servable).unsafeRunSync()
      logger.info("Submitted servable")
      val result = d.get.unsafeRunSync()
      logger.info("Got result")
      fbr.cancel.unsafeRunSync()
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      assert(result.status === Servable.Status.NotServing)
      assert(res.get.unsafeRunSync() === result)
    }

    it("should set NotServing status on probe error") {
      val probe = mock[ServableProbe[IO]]
      when(probe.probe(any)).thenReturn(IO.raiseError(new Exception("OOPSIE") with NoStackTrace))

      val res                          = Deferred.unsafe[IO, Servable]
      val repo: ServableRepository[IO] = mockServableRepoUpsert(res)

      val queue = Queue.unbounded[IO, MonitoringEntry[IO]].unsafeRunSync()
      logger.info("Created queue")
      val cancellableMonitor =
        ServableMonitor.withQueue[IO](queue, 1.seconds, 10.seconds, probe, repo).unsafeRunSync()
      logger.info("Created Monitor")
      val fbr     = cancellableMonitor.fiber
      val monitor = cancellableMonitor.mon
      val d       = monitor.monitor(servable).unsafeRunSync()
      logger.info(s"Submitted ${servable}")
      val result = d.get.unsafeRunSync()
      logger.info(s"Got result ${result}")
      fbr.cancel.unsafeRunSync()
      logger.info(s"Cancelled fiber")
      assert(queue.tryDequeue1.unsafeRunSync() === None)
      logger.info(s"Q is empty")
      assert(result.status === Servable.Status.NotServing)
      logger.info(s"Servable status ${result.status}")
      val pRes = res.get.unsafeRunSync()
      logger.info(s"Deferred result ${pRes}")
      assert(pRes === result)
    }
  }

}

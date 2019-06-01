package io.hydrosphere.serving.manager.domain.servable

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import fs2.concurrent.Queue
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration._

trait ServableMonitor[F[_]] {
  /***
    * Sets a monitor checking the availability of Servable
    *
    * @param servable to be monitored
    * @return a deferred that will be completed when Servable reaches one of final states.
    */
  def monitor(servable: GenericServable): F[Deferred[F, GenericServable]]
}

object ServableMonitor extends Logging {
  type MonitoringEntry[F[_]] = (GenericServable, Deferred[F, GenericServable])

  def default[F[_]](
    monitorSleep: FiniteDuration,
    maxTimeout: FiniteDuration
  )(
    implicit F: Concurrent[F],
    timer: Timer[F],
    probe: ServableProbe[F],
    servableRepository: ServableRepository[F],
  ): F[ServableMonitor[F]] = {
    for {
      queue <- Queue.unbounded[F, MonitoringEntry[F]]
      deathNoteRef <- Ref.of(Map.empty[String, FiniteDuration])
      _ <- monitoringLoop(monitorSleep, maxTimeout, queue, deathNoteRef).foreverM[Unit].start
    } yield new ServableMonitor[F] {
      override def monitor(servable: GenericServable): F[Deferred[F, GenericServable]] = {
        for {
          deferred <- Deferred[F, GenericServable]
          _ <- queue.offer1(servable, deferred)
        } yield deferred
      }
    }
  }

  private def monitoringLoop[F[_] : Concurrent](
    monitorSleep: FiniteDuration,
    maxTimeout: FiniteDuration,
    queue: Queue[F, MonitoringEntry[F]],
    deathNoteRef: Ref[F, Map[String, FiniteDuration]]
  )(
    implicit timer: Timer[F],
    probe: ServableProbe[F],
    servableRepository: ServableRepository[F],
  ): F[Unit] = {
    for {
      entry <- queue.dequeue1
      (servable, deferred) = entry
      name = servable.fullName
      deathNote <- deathNoteRef.get
      status <- probe.probe(servable)
      updatedServable = servable.copy(status = status)
      _ <- status match {
        case _: Servable.Serving =>
          servableRepository.upsert(updatedServable) >>
            deferred.complete(updatedServable) >>
            deathNoteRef.set(deathNote - name).void

        case _: Servable.NotServing =>
          servableRepository.upsert(updatedServable) >>
            deferred.complete(updatedServable) >>
            deathNoteRef.set(deathNote - name).void

        case _: Servable.Starting =>
          queue.offer1(updatedServable, deferred) >>
            deathNoteRef.set(deathNote - name) >>
            timer.sleep(monitorSleep).void

        case Servable.NotAvailable(msg, host, port) =>
          deathNote.get(name) match {
            case Some(timeInTheList) =>
              if (timeInTheList >= maxTimeout) {
                val invalidServable = updatedServable
                  .copy(status = Servable.NotServing(s"Ping timeout exceeded. Info: $msg", host, port))

                deathNoteRef.set(deathNote - name) >>
                  deferred.complete(invalidServable) >>
                  servableRepository.upsert(invalidServable).void
              } else {
                deathNoteRef.set(deathNote + (name -> (timeInTheList + monitorSleep))) >>
                  queue.offer1(updatedServable, deferred) >>
                  timer.sleep(monitorSleep).void
              }
            case None =>
              deathNoteRef.set(deathNote + (name -> monitorSleep)) >>
                queue.offer1(updatedServable, deferred) >>
                timer.sleep(monitorSleep).void
          }
      }
    } yield ()
  }
}
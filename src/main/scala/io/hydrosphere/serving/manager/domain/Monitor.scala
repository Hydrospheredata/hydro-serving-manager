package io.hydrosphere.serving.manager.domain

import cats.effect.implicits._
import cats.effect.{Bracket, Concurrent}
import cats.implicits._
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraph
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableProbe, ServableRepository}
import org.apache.logging.log4j.scala.Logging

object Monitor extends Logging {
  def monitoringLoop[F[_]](
    servableRepository: ServableRepository[F],
    applicationRepository: ApplicationRepository[F],
  )(implicit
    F: Concurrent[F],
    probe: ServableProbe[F]
  ) = {
    loopStep(servableRepository, applicationRepository).foreverM.start
  }

  def loopStep[F[_]](
    servableRepository: ServableRepository[F],
    applicationRepository: ApplicationRepository[F],
  )(implicit
    F: Concurrent[F],
    probe: ServableProbe[F]
  ) = {
    for {
      servables <- servableRepository.all()
      updatedServables <- monitorServables(servables)
      usedApps <- updatedServables.flatTraverse { s =>
        applicationRepository.findServableUsage(s.fullName)
      }
      updatedApps = monitorApps(updatedServables, usedApps)
      _ <- updatedServables.traverse(servableRepository.upsert)
      _ <- updatedApps.traverse(applicationRepository.update)
    } yield ()
    F.unit
  }

  /**
    * Here we need to update app graphs and status
    *
    * @return
    */
  def monitorApps(
    usedServables: List[Servable],
    usedApps: List[Application]
  ): List[Application] = {
    usedApps.map { app =>
      var status: Application.Status = Application.Healthy
      val updatedNodes = app.graph.nodes.map {
        case ExecutionGraph.ModelNode(id, servable) =>
          val updatedServable = usedServables.find(_.fullName == servable.fullName) match {
            case Some(value) => value
            case None => servable
          }
          updatedServable.status match {
            case Servable.Serving(_, _, _) => status
            case Servable.NotServing(msg, _, _) => status = Application.Unhealthy(msg.some)
            case Servable.NotAvailable(msg, _, _) => status = Application.Unhealthy(msg.some)
            case Servable.Starting(msg, _, _) => status = Application.Unhealthy(msg.some)
          }
          ExecutionGraph.ModelNode(id, updatedServable)
        case ExecutionGraph.ABNode(id, submodels, contract) =>
          val updatedSubmodels = submodels.map { case (sub, weight) =>
            val updatedServable = usedServables.find(_.fullName == sub.servable.fullName) match {
              case Some(value) => value
              case None => sub.servable
            }
            updatedServable.status match {
              case Servable.Serving(_, _, _) => status
              case Servable.NotServing(msg, _, _) => status = Application.Unhealthy(msg.some)
              case Servable.NotAvailable(msg, _, _) => status = Application.Unhealthy(msg.some)
              case Servable.Starting(msg, _, _) => status = Application.Unhealthy(msg.some)
            }
            ExecutionGraph.ModelNode(id, updatedServable) -> weight
          }
          ExecutionGraph.ABNode(id, updatedSubmodels, contract)
        case x: ExecutionGraph.InternalNode => x
      }
      val updatedGraph = app.graph.copy(nodes = updatedNodes)
      app.copy(graph = updatedGraph, status = status)
    }
  }

  def monitorServables[F[_]](
    servables: List[Servable]
  )(implicit
    F: Bracket[F, Throwable],
    probe: ServableProbe[F]
  ): F[List[Servable]] = {
    for {
      maybeUpdatedServables <- servables.traverse(x => handleServable(x))
    } yield maybeUpdatedServables.flatten
  }

  def handleServable[F[_]](
    servable: Servable
  )(implicit
    F: Bracket[F, Throwable],
    probe: ServableProbe[F]
  ): F[Option[Servable]] = {
    for {
      newStatus <- probe.probe(servable).handleError { x =>
        Servable.NotAvailable(s"Probe failed. ${x.getMessage}", None, None)
      }
      maybeUpdated = if (newStatus != servable.status) {
        Some(servable.copy(status = newStatus))
      } else {
        None
      }
    } yield maybeUpdated
  }
}
package io.hydrosphere.serving.manager.domain

import cats.Monad
import cats.effect.{Bracket, Concurrent}
import cats.implicits._
import cats.effect.implicits._
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
      servables <- servableRepository.all().compile.toList
      updatedServables <- monitorServables(servables)
      usedApps <- updatedServables.traverse{ s =>
        applicationRepository.findServableUsage(s.fullName).compile.toList.map(x => s -> x)
      }
      usedAppsMap = usedApps.toMap
      updatedApps <- monitorApps(usedAppsMap)
      _ <- updatedServables.traverse(servableRepository.upsert)
      _ <- updatedServables.traverse(applicationRepository.update)
    } yield ()
    F.unit
  }

  def monitorApps[F[_]](
    servableDiff: Map[Servable, List[Application]]
  )(implicit
  F: Monad[F]
  ) = {
    val statusDiff = servableDiff.flatMap{
      case (servable, apps) =>
        servable.status match {
          case Servable.Serving(msg, host, port) =>
            apps.map {app =>
              app.graph.nodes.find{ x =>
                x match {
                  case ExecutionGraph.ModelNode(id, mv, es) =>
                    val x = es.find(_.fullName == servable.fullName)
                  case ExecutionGraph.ABNode(id, submodels, signature) =>
                  case ExecutionGraph(id, nodes, links, signature) =>
                }
              }
              app.copy(status = Application.Healthy)
            }
          case _ =>
            apps.map { app =>
              app.copy(status = Application.Unhealthy)
            }
        }
    }
    // Now we merge app state diffs so there are no dupes.
    // If there is Healthy :: Healthy :: Unhealthy states, they are reduced to Unhealthy
    statusDiff.groupBy(_.id).map{
      case (_, apps) => apps.map(_.status).fold(Application.Healthy) {
        case (Application.Unhealthy, _) => Application.Unhealthy
      }
    }
  }

  def monitorServables[F[_]](
    servables: List[Servable]
  )(implicit
    F: Bracket[F, Throwable],
    probe: ServableProbe[F]
  ) = {
    for {
      maybeUpdatedServables <- servables.traverse(handleServable)
    } yield maybeUpdatedServables.flatten
  }

  def handleServable[F[_]](servable: Servable)(implicit F:Bracket[F, Throwable], probe: ServableProbe[F]) = {
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
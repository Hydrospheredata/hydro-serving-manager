package io.hydrosphere.serving.manager.api.http.controller.application

import cats.effect.Effect
import cats.syntax.all._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.requests.{CreateApplicationRequest, UpdateApplicationRequest}
import javax.ws.rs.Path

@Path("/application")
class ApplicationController[F[_]: Effect](
  appService: ApplicationService[F],
)extends AkkaHttpControllerDsl {

  @Path("/")
  def listAll = path("application") {
    get {
      completeF{
        for {
          apps <- appService.all()
        } yield apps.map(ApplicationView.fromApplication)
      }
    }
  }

  @Path("/{appName}")
  def getApp = path("application" / Segment) { appName =>
    get {
      completeF{
        for {
          app <- appService.get(appName)
        } yield ApplicationView.fromApplication(app)
      }
    }
  }

  @Path("/")
  def create = path("application") {
    post {
      entity(as[CreateApplicationRequest]) { r =>
        completeF{
          for {
            app <- appService.create(r)
          } yield ApplicationView.fromApplication(app)
        }
      }
    }
  }

  @Path("/")
  def update = pathPrefix("application") {
    put {
      entity(as[UpdateApplicationRequest]) { r =>
        completeF{
          for {
            app <- appService.update(r)
          } yield ApplicationView.fromApplication(app)
        }
      }
    }
  }

  @Path("/{applicationName}")
  def deleteApplicationByName = delete {
    path("application" / Segment) { appName =>
      completeF{
        for {
          app <- appService.delete(appName)
        } yield ApplicationView.fromApplication(app)
      }
    }
  }


  @Path("/generateInputs/{applicationName}/")
  def generateInputsForApp = pathPrefix("application" / "generateInputs" / Segment) { appName =>
    get {
      completeF(
        appService.generateInputs(appName)
      )
    }
  }

  val routes =
    listAll ~
      create ~
      update ~
      deleteApplicationByName ~
      generateInputsForApp ~
      getApp
}
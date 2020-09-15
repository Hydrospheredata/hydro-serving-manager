package io.hydrosphere.serving.manager.api.http.controller.application

import cats.effect.Effect
import cats.syntax.all._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.requests.{CreateApplicationRequest, UpdateApplicationRequest}
import io.swagger.annotations._
import javax.ws.rs.Path

@Path("/application")
@Api(produces = "application/json", tags = Array("Application"))
class ApplicationController[F[_]: Effect](
  appService: ApplicationService[F],
)extends AkkaHttpControllerDsl {

  @Path("/")
  @ApiOperation(value = "applications", notes = "applications", nickname = "applications", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Application", response = classOf[ApplicationView], responseContainer = "List"),
  ))
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
  @ApiOperation(value = "getApp", notes = "getApp", nickname = "getApp", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "appName", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Application", response = classOf[ApplicationView], responseContainer = "List"),
    new ApiResponse(code = 404, message = "Not Found"),
  ))
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
  @ApiOperation(value = "Add Application", notes = "Add Application", nickname = "addApplication", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "Application", required = true,
      dataTypeClass = classOf[CreateApplicationRequest], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Application", response = classOf[ApplicationView]),
  ))
  def create = path("application") {
    post {
      entity(as[CreateApplicationRequest]) { r =>
        completeF{
          for {
            app <- appService.create(r)
          } yield ApplicationView.fromApplication(app.started)
        }
      }
    }
  }

  @Path("/")
  @ApiOperation(value = "Update Application", notes = "Update Application", nickname = "updateApplication", httpMethod = "PUT")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "ApplicationCreateOrUpdateRequest", required = true,
      dataTypeClass = classOf[UpdateApplicationRequest], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Application", response = classOf[ApplicationView]),
  ))
  def update = pathPrefix("application") {
    put {
      entity(as[UpdateApplicationRequest]) { r =>
        completeF{
          for {
            app <- appService.update(r)
          } yield ApplicationView.fromApplication(app.started)
        }
      }
    }
  }

  @Path("/{applicationName}")
  @ApiOperation(value = "deleteApplication", notes = "deleteApplication", nickname = "deleteApplication", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "applicationName", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Application Deleted"),
  ))
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
  @ApiOperation(value = "Generate payload for application", notes = "Generate payload for application", nickname = "Generate payload for application", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "applicationName", required = true, dataType = "string", paramType = "path", value = "name"),
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Any"),
  ))
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
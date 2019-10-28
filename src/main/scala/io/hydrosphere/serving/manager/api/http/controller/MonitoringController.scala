package io.hydrosphere.serving.manager.api.http.controller


class MonitoringController extends AkkaHttpControllerDsl {
  def createSpec = pathEnd {
    post {
      entity(as[String]) { b => // TODO request entity
        complete(???)
      }
    }
  }

  def listSpecs = pathEnd {
    get {
      complete(???)
    }
  }

  def getSpec = path(Segment) { id =>
    get {
      complete(???)
    }
  }

  def deleteSpec = path(Segment) { id =>
    delete {
      complete(???)
    }
  }

  def routes = pathPrefix("monitoring") {
    createSpec ~ listSpecs ~ getSpec ~ deleteSpec
  }
}

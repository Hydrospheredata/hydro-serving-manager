package io.hydrosphere.serving.manager.infrastructure.grpc

import io.grpc._

final class BuilderWrapper[T <: ServerBuilder[T]](private val builder: ServerBuilder[T])
    extends AnyVal {
  def addService(service: ServerServiceDefinition): BuilderWrapper[T] = {
    new BuilderWrapper(builder.addService(service))
  }

  def intercept(service: ServerInterceptor): BuilderWrapper[T] = {
    new BuilderWrapper(builder.intercept(service))
  }

  def build: Server = {
    builder.build()
  }
}

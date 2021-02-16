package io.hydrosphere.serving.manager.api.grpc

import cats.effect.Sync

import io.hydrosphere.serving.grpc.BuilderWrapper
import io.hydrosphere.serving.proto.discovery.api.ServingDiscoveryGrpc
import io.hydrosphere.serving.proto.manager.api.ManagerServiceGrpc
import io.hydrosphere.serving.manager.config.ManagerConfiguration

import scala.concurrent.ExecutionContext

trait GrpcServer[F[_]] {
  def start(): F[Unit]
  def shutdown(): F[Unit]
}

object GrpcServer {
  def default[F[_]](
      config: ManagerConfiguration,
      managerGrpcService: ManagerGrpcService[F],
      discoveryService: GrpcServingDiscovery[F]
  )(implicit
      F: Sync[F],
      ex: ExecutionContext
  ): GrpcServer[F] = {
    val builder = BuilderWrapper(io.grpc.ServerBuilder.forPort(config.application.grpcPort))
      .addService(ManagerServiceGrpc.bindService(managerGrpcService, ex))
      .addService(ServingDiscoveryGrpc.bindService(discoveryService, ex))

    val underServer = builder.build
    new GrpcServer[F] {
      override def start(): F[Unit] = F.delay(underServer.start())

      override def shutdown(): F[Unit] = F.delay(underServer.shutdownNow())
    }
  }
}

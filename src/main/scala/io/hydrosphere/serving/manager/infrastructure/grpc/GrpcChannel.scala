package io.hydrosphere.serving.manager.infrastructure.grpc

import cats.effect.{Resource, Sync}
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object GrpcChannel {

  trait Factory[F[_]] {
    def make(host: String, port: Int): Resource[F, ManagedChannel]
  }

  class PlaintextFactory[F[_]](implicit F: Sync[F]) extends GrpcChannel.Factory[F] {
    def make(host: String, port: Int): Resource[F, ManagedChannel] = {
      val ch = F.delay {
        val builder = ManagedChannelBuilder.forAddress(host, port)
        builder.usePlaintext()
        builder.build()
      }
      Resource.make(ch)(x => F.delay(x.shutdownNow()))
    }
  }

}

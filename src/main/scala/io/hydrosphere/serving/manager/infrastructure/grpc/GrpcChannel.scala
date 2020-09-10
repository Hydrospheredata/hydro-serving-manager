package io.hydrosphere.serving.manager.infrastructure.grpc

import cats.effect.{Resource, Sync}
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object GrpcChannel {

 trait Factory[F[_]] {
  def make(host: String, port: Int): Resource[F, ManagedChannel]
 }

 def plaintextFactory[F[_]](implicit F: Sync[F]): GrpcChannel.Factory[F] =
  new GrpcChannel.Factory[F] {
   def make(host: String, port: Int) = {
    val ch = F.delay {
     val builder = if (host.startsWith("dns:")) {
      ManagedChannelBuilder.forTarget(s"$host:$port")
     } else {
      ManagedChannelBuilder.forAddress(host, port)
     }
     builder.usePlaintext()
     builder.build()
    }
    Resource.make(ch)(x => F.delay(x.shutdownNow()))
   }
  }

}
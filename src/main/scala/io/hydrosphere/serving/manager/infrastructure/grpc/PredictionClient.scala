package io.hydrosphere.serving.manager.infrastructure.grpc

import cats.effect.{Async, Resource}
import com.google.protobuf.empty.Empty
import io.hydrosphere.serving.manager.util.AsyncUtil
import io.hydrosphere.serving.tensorflow.api.prediction_service.{
  PredictionServiceGrpc,
  StatusResponse
}

import scala.concurrent.ExecutionContext

trait PredictionClient[F[_]] {
  def status(): F[StatusResponse]
}

object PredictionClient {

  trait Factory[F[_]] {
    def make(host: String, port: Int): Resource[F, PredictionClient[F]]
  }

  class DefaultFactory[F[_]](channelCtor: GrpcChannel.Factory[F])(implicit
      F: Async[F],
      ec: ExecutionContext
  ) extends Factory[F] {
    override def make(host: String, port: Int): Resource[F, PredictionClient[F]] =
      for {
        channel <- channelCtor.make(host, port)
        stub = PredictionServiceGrpc.stub(channel)
      } yield new PredictionClient[F] {
        override def status(): F[StatusResponse] = AsyncUtil.futureAsync(stub.status(Empty()))
      }
  }

}

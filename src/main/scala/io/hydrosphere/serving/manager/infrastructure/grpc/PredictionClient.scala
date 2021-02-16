package io.hydrosphere.serving.manager.infrastructure.grpc

//import cats.effect.{Async, Resource}
//import com.google.protobuf.empty.Empty
//import io.hydrosphere.serving.manager.util.AsyncUtil
//import io.hydrosphere.serving.proto.runtime.api.PredictionServiceGrpc
//import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse

//import scala.concurrent.ExecutionContext

trait PredictionClient[F[_]] {
//  def status(): F[StatusResponse]
}

object PredictionClient {

  trait Factory[F[_]] {
//    def make(host: String, port: Int): Resource[F, PredictionClient[F]]
  }

//  def clientCtor[F[_]: Async](
//      channelCtor: GrpcChannel.Factory[F]
//  )(implicit ec: ExecutionContext): Factory[F] =
//    (host: String, port: Int) => {
//      for {
//        channel <- channelCtor.make(host, port)
//        stub = PredictionServiceGrpc.stub(channel)
//      } yield new PredictionClient[F] {
//        override def status(): F[StatusResponse] = AsyncUtil.futureAsync(stub.status(Empty()))
//      }
//    }
}

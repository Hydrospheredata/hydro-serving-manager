package io.hydrosphere.serving.manager.infrastructure.storage.fetchers

import java.nio.file.Path

import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import io.hydrosphere.serving.manager.domain.contract.Contract
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras.KerasFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow.TensorflowModelFetcher
import io.hydrosphere.serving.manager.util.UnsafeLogging

case class FetcherResult(
    modelName: String,
    modelContract: Contract,
    metadata: Map[String, String]
)

trait ModelFetcher[F[_]] {
  def fetch(path: Path): F[Option[FetcherResult]]
}

object ModelFetcher extends UnsafeLogging {
  def default[F[_]: Sync: StorageOps]() = {
    val storageOps = StorageOps[F]
    combinedFetcher(
      NonEmptyList.of(
        new SparkModelFetcher[F](storageOps),
        new TensorflowModelFetcher[F](storageOps),
        new ONNXFetcher[F](storageOps),
        new KerasFetcher[F](storageOps),
        new FallbackContractFetcher[F](storageOps)
      )
    )
  }

  /**
    * Sequentially applies fetchers and returns the first successful result
    *
    */
  def combinedFetcher[F[_]](
      fetchers: NonEmptyList[ModelFetcher[F]]
  )(
      implicit F: MonadError[F, Throwable]
  ): ModelFetcher[F] = {
    new ModelFetcher[F] {
      override def fetch(path: Path): F[Option[FetcherResult]] = {
        fetchers
          .traverse(fetcher => fetcher.fetch(path).attempt)
          .map { results =>
            results
              .collectFirst {
                case Right(Some(result)) => result
              }
          }
      }
    }
  }
}

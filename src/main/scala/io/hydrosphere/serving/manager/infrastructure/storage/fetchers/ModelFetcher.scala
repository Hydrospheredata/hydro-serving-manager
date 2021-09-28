package io.hydrosphere.serving.manager.infrastructure.storage.fetchers

import java.nio.file.Path
import cats.effect.Sync
import cats.implicits._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras.KerasFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow.TensorflowModelFetcher
import org.apache.logging.log4j.scala.Logging

case class FetcherResult(
    modelName: String,
    modelSignature: Signature,
    metadata: Map[String, String]
)

trait ModelFetcher[F[_]] {
  def fetch(path: Path): F[Option[FetcherResult]]
}

object ModelFetcher extends Logging {
  def default[F[_]: Sync](implicit storageOps: StorageOps[F]) =
    combine(
      Seq(
        new SparkModelFetcher[F](storageOps),
        new TensorflowModelFetcher[F](storageOps),
        new KerasFetcher[F](storageOps),
        new FallbackContractFetcher[F](storageOps)
      )
    )

  /**
    * Sequentially applies fetchers and returns the first successful result
    *
    * @param fetchers
    * @tparam F
    * @return
    */
  def combine[F[_]: Sync](fetchers: Seq[ModelFetcher[F]]) =
    new ModelFetcher[F] {
      override def fetch(path: Path): F[Option[FetcherResult]] = {
        val safeFetch = (x: ModelFetcher[F]) =>
          x.fetch(path).attempt.flatMap { x =>
            x.fold(
              ex =>
                Sync[F].delay(logger.warn("ModelFetcher fail", ex)) >>
                  none[FetcherResult].pure[F],
              value => value.pure[F]
            )
          }
        for {
          fetchResults <- fetchers.traverse(safeFetch)
        } yield fetchResults.flatten.headOption
      }
    }
}

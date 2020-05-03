package io.hydrosphere.serving.manager.infrastructure.storage.fetchers

import java.nio.file.Path

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.contract.Contract
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.UnsafeLogging

class FallbackContractFetcher[F[_]](
    source: StorageOps[F]
)(implicit F: Sync[F])
    extends ModelFetcher[F]
    with UnsafeLogging {
  override def fetch(directory: Path): F[Option[FetcherResult]] = {
    getContract(directory).toOption.map { contract =>
      FetcherResult(
        modelName = directory.getFileName.toString,
        modelContract = contract,
        metadata = Map.empty
      )
    }.value
  }

  private def getContract(modelPath: Path) = {
    val txtContract = for {
      metaFile <- source.readText(modelPath.resolve("contract.prototxt"))
      text = metaFile.mkString
      contract <- F.delay(ModelContract.fromAscii(text))
    } yield contract

    val binContract = for {
      metaFile <- source.readBytes(modelPath.resolve("contract.protobin"))
      contract <- F.delay(ModelContract.parseFrom(metaFile))
    } yield contract

    EitherT(
      txtContract.attempt
        .orElse(binContract.attempt)
    ).flatMap(x => EitherT.fromEither[F](Contract.fromProto(x)))
  }
}

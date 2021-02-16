package io.hydrosphere.serving.manager.infrastructure.storage.fetchers

import java.nio.file.Path
import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.implicits._

import io.hydrosphere.serving.proto.contract.signature.ModelSignature

import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.UnsafeLogging

class FallbackContractFetcher[F[_]](
    source: StorageOps[F]
)(implicit F: Sync[F])
    extends ModelFetcher[F]
    with UnsafeLogging {
  override def fetch(directory: Path): F[Option[FetcherResult]] = {
    val contract = getContract(directory)

    contract.map { signature =>
      FetcherResult(
        modelName = directory.getFileName.toString,
        modelSignature = signature,
        metadata = Map.empty
      )
    }.value
  }

  private def getContract(modelPath: Path) = {
    val txtContract: F[ModelSignature] = for {
      metaFile <- source.readText(modelPath.resolve("contract.prototxt"))
      contract <- F.delay(ModelSignature.fromAscii(metaFile.get.mkString))
    } yield contract

    val binContract: OptionT[F, ModelSignature] = for {
      metaFile <- OptionT(source.readBytes(modelPath.resolve("contract.protobin")))
      contract <- OptionT.liftF(F.delay(ModelSignature.parseFrom(metaFile)))
    } yield contract

    val faillessTxtContract = EitherT(txtContract.attempt).toOption
    val faillessBinContract = OptionT(binContract.value)

    faillessTxtContract
      .orElse(faillessBinContract)
      .flatMap(proto => EitherT.fromEither[F](Signature.fromProto(proto)).toOption)
  }
}

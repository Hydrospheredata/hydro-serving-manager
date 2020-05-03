package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow

import java.nio.file.Path

import cats.Monad
import cats.implicits._
import cats.data.{NonEmptyList, OptionT}
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers._
import io.hydrosphere.serving.tensorflow.types.{DataType => ProtoDataType}
import org.tensorflow.framework.{SavedModel, SignatureDef, TensorInfo}

import scala.jdk.CollectionConverters._
import scala.util.Try

class TensorflowModelFetcher[F[_]: Monad](storageOps: StorageOps[F]) extends ModelFetcher[F] {

  def parseMetadata(savedModel: SavedModel): Map[String, String] = {
    val metagraphdata = savedModel.getMetaGraphsList.asScala.zipWithIndex.flatMap {
      case (mg, id) =>
        val collectionsCount     = Option(mg.getCollectionDefCount)
        val assetFilesCount      = Option(mg.getAssetFileDefCount)
        val serializedSize       = Option(mg.getSerializedSize)
        val signatureCount       = Option(mg.getSignatureDefCount)
        val metaInfo             = Option(mg.getMetaInfoDef)
        val mgVersion            = metaInfo.flatMap(x => Option(x.getMetaGraphVersion))
        val strippedDefaultAttrs = metaInfo.flatMap(x => Option(x.getStrippedDefaultAttrs))
        val tagsCount            = metaInfo.flatMap(x => Option(x.getTagsCount))
        val tensorflowGitVersion = metaInfo.flatMap(x => Option(x.getTensorflowGitVersion))
        val tensorflowVersion    = metaInfo.flatMap(x => Option(x.getTensorflowVersion))
        Map(
          s"tensorflow.metaGraph[$id].collectionsCount"     -> collectionsCount,
          s"tensorflow.metaGraph[$id].assetFilesCount"      -> assetFilesCount,
          s"tensorflow.metaGraph[$id].serializedSize"       -> serializedSize,
          s"tensorflow.metaGraph[$id].signatureCount"       -> signatureCount,
          s"tensorflow.metaGraph[$id].metaGraphVersion"     -> mgVersion,
          s"tensorflow.metaGraph[$id].strippedDefaultAttrs" -> strippedDefaultAttrs,
          s"tensorflow.metaGraph[$id].tagsCount"            -> tagsCount,
          s"tensorflow.metaGraph[$id].tensorflowGitVersion" -> tensorflowGitVersion,
          s"tensorflow.metaGraph[$id].tensorflowVersion"    -> tensorflowVersion
        ).collect({ case (k, Some(v)) => k -> v.toString })
    }.toMap

    val mgCount = Option(savedModel.getMetaGraphsCount)
    val savedModelData = Map("tensorflow.metaGraphsCount" -> mgCount).collect({
      case (k, Some(v)) => k -> v.toString
    })

    val all = (metagraphdata ++ savedModelData)
      .collect {
        case (k, v) if v.trim.nonEmpty => k -> v.trim
      }
    all
  }

  override def fetch(directory: Path): F[Option[FetcherResult]] = {
    val f = for {
      savedModelBytes  <- OptionT.liftF(storageOps.readBytes(directory.resolve("saved_model.pb")))
      savedModel       <- OptionT.fromOption[F](Try(SavedModel.parseFrom(savedModelBytes)).toOption)
      predictSignature <- OptionT.fromOption[F](getPredictSignature(savedModel))
      modelName = directory.getFileName.toString
    } yield FetcherResult(
      modelName = modelName,
      modelContract = Contract(predictSignature),
      metadata = parseMetadata(savedModel)
    )
    f.value
  }

  def getPredictSignature(savedModel: SavedModel) = {
    val servingSignatures = Try {
      savedModel.getMetaGraphsList.asScala
        .filter(_.getMetaInfoDef.getTagsList.asScala.contains(TensorflowModelFetcher.serveTag))
        .flatMap(_.getSignatureDefMap.asScala)
        .toList
    }.toOption
    servingSignatures.flatMap { s =>
      val defaultSig = s.find(_._1 == TensorflowModelFetcher.servingDefaultSig)
      val predictSig = s.find(_._1 == TensorflowModelFetcher.predictSig)
      val result     = defaultSig orElse predictSig orElse s.headOption
      result.flatMap(TensorflowModelFetcher.convertSignature)
    }
  }
}

object TensorflowModelFetcher {

  /**
    * Tag which indicates MetaGraph suitable for serving.
    */
  final val serveTag = "serve"

  final val servingDefaultSig = "serving_default"

  final val predictSig = "predict"

  def convertTensor(tensorInfo: TensorInfo): Option[FieldInfo] = {
    val shape = if (tensorInfo.hasTensorShape) {
      val tShape = tensorInfo.getTensorShape
      val dims   = tShape.getDimList.asScala.toList.map(x => x.getSize)
      TensorShape.Static(dims)
    } else TensorShape.Dynamic
    val convertedDtype = DataType.fromProto(ProtoDataType.fromValue(tensorInfo.getDtypeValue))
    convertedDtype.map(FieldInfo(_, shape))
  }

  def convertTensorMap(tensorMap: Map[String, TensorInfo]): Option[List[Field]] = {
    tensorMap.toList.traverse {
      case (inputName, inputDef) =>
        convertTensor(inputDef).map(info =>
          Field.Tensor(
            name = inputName,
            shape = info.shape,
            dtype = info.dataType,
            profile = None
          )
        )
    }
  }

  def convertSignature(signatureKV: (String, SignatureDef)): Option[Signature] = {
    for {
      inputs  <- convertTensorMap(signatureKV._2.getInputsMap.asScala.toMap)
      outputs <- convertTensorMap(signatureKV._2.getOutputsMap.asScala.toMap)

      inputNEL  <- NonEmptyList.fromList(inputs)
      outputNEL <- NonEmptyList.fromList(outputs)
    } yield Signature(
      signatureName = signatureKV._1,
      inputs = inputNEL,
      outputs = outputNEL
    )
  }
}

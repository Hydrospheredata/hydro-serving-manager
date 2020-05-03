package io.hydrosphere.serving.manager.infrastructure.storage.fetchers

import java.nio.file.{Files, Path}

import cats.Monad
import cats.implicits._
import cats.data.{NonEmptyList, OptionT}
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.onnx.onnx.TensorProto.DataType._
import io.hydrosphere.serving.onnx.onnx._
import org.apache.commons.io.FilenameUtils

import scala.util.Try

class ONNXFetcher[F[_]: Monad](
    storageOps: StorageOps[F]
) extends ModelFetcher[F] {
  def findFile(directory: Path): OptionT[F, Path] = {
    for {
      dirFile <- OptionT(storageOps.getReadableFile(directory))
      onnxFile <- OptionT.fromOption[F](
        dirFile.listFiles().find(f => f.isFile && f.getName.endsWith(".onnx")).map(_.toPath)
      )
    } yield onnxFile
  }

  def modelMetadata(model: ModelProto): Map[String, String] = {
    val basic = Map(
      "onnx.producerName"    -> model.producerName,
      "onnx.producerVersion" -> model.producerVersion,
      "onnx.domain"          -> model.domain,
      "onnx.irVersion"       -> model.irVersion.toString,
      "onnx.modelVersion"    -> model.modelVersion.toString,
      "onnx.docString"       -> model.docString
    ).map { case (k, v) => k -> v.trim }
      .filter { // filter proto default strings
        case (_, s) => s.nonEmpty
      }

    val props = model.metadataProps.map { x => ("onnx.metadata." + x.key) -> x.value }.toMap

    basic ++ props
  }

  override def fetch(directory: Path): F[Option[FetcherResult]] = {
    val f = for {
      filePath <- findFile(directory)
      fileName = FilenameUtils.getBaseName(filePath.getFileName.toString)
      model     <- OptionT.fromOption[F](ModelProto.validate(Files.readAllBytes(filePath)).toOption)
      graph     <- OptionT.fromOption[F](model.graph)
      signature <- OptionT.fromOption[F](Try(ONNXFetcher.predictSignature(graph)).toOption.flatten)
    } yield {
      FetcherResult(
        fileName,
        Contract(signature),
        metadata = modelMetadata(model)
      )
    }
    f.value
  }
}

object ONNXFetcher {
  final val signature = "Predict"

  def convertType(elemType: TensorProto.DataType) = {
    elemType match {
      case FLOAT      => DataType.DT_FLOAT.some
      case UINT8      => DataType.DT_UINT8.some
      case INT8       => DataType.DT_INT8.some
      case UINT16     => DataType.DT_UINT16.some
      case INT16      => DataType.DT_INT16.some
      case INT32      => DataType.DT_INT32.some
      case INT64      => DataType.DT_INT64.some
      case STRING     => DataType.DT_STRING.some
      case BOOL       => DataType.DT_BOOL.some
      case FLOAT16    => DataType.DT_FLOAT.some
      case DOUBLE     => DataType.DT_DOUBLE.some
      case UINT32     => DataType.DT_UINT32.some
      case UINT64     => DataType.DT_UINT64.some
      case COMPLEX64  => DataType.DT_COMPLEX64.some
      case COMPLEX128 => DataType.DT_COMPLEX128.some
      case _          => None
    }
  }

  def convertShape(shape: Option[TensorShapeProto]): TensorShape = {
    shape
      .map { realShape =>
        val dims = realShape.dim.map { realDim => realDim.value.dimValue.get }.toList
        TensorShape.Static(dims)
      }
      .getOrElse(TensorShape.Dynamic)
  }

  def valueInfoToField(x: ValueInfoProto): Option[Field] = {
    convertType(x.getType.getTensorType.elemType).map { dtype =>
      Field.Tensor(
        name = x.name,
        shape = convertShape(x.getType.getTensorType.shape),
        dtype = dtype,
        profile = None
      )
    }
  }

  def predictSignature(graph: GraphProto) = {
    for {
      inputs     <- graph.input.toList.traverse(valueInfoToField)
      outputs    <- graph.output.toList.traverse(valueInfoToField)
      inputsNEL  <- NonEmptyList.fromList(inputs)
      outputsNEL <- NonEmptyList.fromList(outputs)
    } yield Signature(
      signature,
      inputsNEL,
      outputsNEL
    )
  }
}

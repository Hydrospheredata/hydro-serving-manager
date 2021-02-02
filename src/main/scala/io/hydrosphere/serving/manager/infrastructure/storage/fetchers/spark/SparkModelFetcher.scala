package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark

import java.nio.file.Path

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Sync
import cats.implicits._
import io.circe.parser._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers.SparkMlTypeMapper
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.{FetcherResult, ModelFetcher}
import io.hydrosphere.serving.manager.util.UnsafeLogging

class SparkModelFetcher[F[_]](storageOps: StorageOps[F])(implicit F: Sync[F])
    extends ModelFetcher[F]
    with UnsafeLogging {
  private def getStageMetadata(stagesPath: Path, stage: String): F[SparkModelMetadata] =
    getMetadata(stagesPath.resolve(stage))

  private def getMetadata(model: Path): F[SparkModelMetadata] =
    for {
      lines <- storageOps.readText(model.resolve("metadata/part-00000"))
      text = lines.get.mkString
      json     <- F.fromEither(parse(text))
      metadata <- F.fromEither(json.as[SparkModelMetadata])
    } yield metadata

  override def fetch(directory: Path): F[Option[FetcherResult]] = {
    val modelName = directory.getFileName.toString
    val meta      = getMetadata(directory)

    val f = for {
      metadata  <- OptionT.liftF(getMetadata(directory))
      signature <- OptionT(processPipeline(directory.resolve("stages")))
    } yield FetcherResult(
      modelName = modelName,
      modelSignature = signature,
      metadata = metadata.toMap
    )

    f.value
  }

  private def processPipeline(stagesDir: Path) =
    for {
      stages    <- storageOps.getSubDirs(stagesDir)
      sM        <- stages.traverse(x => getStageMetadata(stagesDir, x))
      signature <- SparkModelFetcher.processStages(sM)
    } yield signature
}

object SparkModelFetcher {
  def processStages[F[_]](
      stagesMetadata: List[SparkModelMetadata]
  )(implicit F: Sync[F]): F[Option[Signature]] =
    F.delay {
      val mappers = stagesMetadata.map(SparkMlTypeMapper.apply)
      for {
        inputs  <- mappers.traverse(_.inputSchema)
        outputs <- mappers.traverse(_.outputSchema)
        labels    = mappers.flatMap(_.labelSchema)
        allLabels = labels.map(_.name)
        allIns    = inputs.flatten.map(x => x.name -> x).toMap
        allOuts   = outputs.flatten.map(x => x.name -> x).toMap
        inputSchema =
          if (allLabels.isEmpty)
            (allIns -- allOuts.keys).map { case (_, y) => y }.toList
          else {
            val trainInputs = stagesMetadata
              .filter { stage =>
                val mapper = SparkMlTypeMapper(stage)
                mapper.outputSchema.getOrElse(Nil).map(_.name).containsSlice(allLabels)
              }
              .flatTraverse(stage => SparkMlTypeMapper(stage).inputSchema)
            val allTrains = trainInputs.getOrElse(Nil).map(x => x.name -> x).toMap
            (allIns -- allOuts.keys -- allTrains.keys).map { case (_, y) => y }.toList
          }
        outputSchema = (allOuts -- allIns.keys).map { case (_, y) => y }.toList

        ins  <- NonEmptyList.fromList(inputSchema)
        outs <- NonEmptyList.fromList(outputSchema)
      } yield {
        val signature = Signature("default_spark", ins, outs)
        signature
      }
    }
}

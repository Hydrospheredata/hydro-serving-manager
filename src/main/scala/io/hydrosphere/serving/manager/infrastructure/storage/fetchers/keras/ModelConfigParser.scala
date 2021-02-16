package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras

import java.nio.file.Path

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Monad, MonadError}
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FetcherResult
import io.hydrosphere.serving.manager.util.{HDF5File, UnsafeLogging}
import org.apache.commons.io.FilenameUtils
import io.hydrosphere.serving.manager.util.JsonOps._

private[keras] trait ModelConfigParser[F[_]] {
  def importModel: F[FetcherResult]
}

private[keras] object ModelConfigParser extends UnsafeLogging {
  def importer[F[_]: Sync](
      source: StorageOps[F],
      directory: Path
  ): F[Option[ModelConfigParser[F]]] = {
    val f = for {
      h5Path <- findH5file(source, directory)
    } yield ModelConfigParser.H5(source, h5Path).asInstanceOf[ModelConfigParser[F]]
    f.value
  }

  def findH5file[F[_]: Monad](source: StorageOps[F], directory: Path) =
    for {
      dirFile <- OptionT(source.getReadableFile(directory))
      file <- OptionT.fromOption(
        dirFile.listFiles().find(f => f.isFile && f.getName.endsWith(".h5")).map(_.toPath)
      )
    } yield file

  case class H5[F[_]: Sync](source: StorageOps[F], h5path: Path) extends ModelConfigParser[F] {
    def importModel: F[FetcherResult] = {
      val h5 = Sync[F].delay(HDF5File(h5path.toString))
      Sync[F].bracketCase(h5) { h5File =>
        for {
          modelName <- Sync[F].delay(
            FilenameUtils.removeExtension(h5path.getFileName.getFileName.toString)
          )
          jsonModelConfig <- Sync[F].delay(h5File.readAttributeAsString("model_config"))
          kerasVersion    <- Sync[F].delay(h5File.readAttributeAsString("keras_version"))
          model           <- JsonString(jsonModelConfig, modelName, kerasVersion).importModel
        } yield model
      } {
        case (a, _) => Sync[F].delay(a.close())
      }
    }
  }

  case class JsonString[F[_]](modelConfigJson: String, name: String, version: String)(implicit
      F: MonadError[F, Throwable]
  ) extends ModelConfigParser[F] {

    override def importModel: F[FetcherResult] =
      for {
        config <- F.fromEither(modelConfigJson.parseJsonAs[ModelConfig])
        signature <- F.fromOption(
          config.toPredictSignature,
          new IllegalArgumentException(s"Can't extract predict signature from ${config}")
        )
      } yield FetcherResult(
        modelName = name,
        modelSignature = signature,
        metadata = Map.empty
      )
  }
}

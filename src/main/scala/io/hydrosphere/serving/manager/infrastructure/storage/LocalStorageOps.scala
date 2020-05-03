package io.hydrosphere.serving.manager.infrastructure.storage

import java.io.{File, IOException}
import java.nio.file._

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import org.apache.commons.io.{FileUtils => ApacheFS}
import io.hydrosphere.serving.manager.util.FileUtils._

import scala.jdk.CollectionConverters._

class LocalStorageOps[F[_]](implicit F: Sync[F]) extends StorageOps[F] {

  override def getReadableFile(path: Path): F[Option[File]] = Sync[F].delay {
    if (Files.exists(path)) Some(path.toFile) else None
  }

  def getFileOrFail(path: Path): F[File] =
    OptionT(getReadableFile(path))
      .getOrElseF(F.raiseError(new IOException(s"File $path is not found")))

  override def getSubDirs(path: Path): F[List[String]] = {
    getFileOrFail(path)
      .map { dirPath =>
        dirPath.getSubDirectories
          .map(_.getName)
          .toList
      }
  }

  override def getAllFiles(path: Path): F[List[String]] = {
    getFileOrFail(path)
      .map { file =>
        val fullUri = file.toURI
        file.listFilesRecursively
          .map(p => fullUri.relativize(p.toURI).toString)
          .toList
      }
  }

  override def exists(path: Path): F[Boolean] = {
    getReadableFile(path).map(_.isDefined)
  }

  override def copyFile(src: Path, target: Path): F[Path] = Sync[F].delay {
    val parentPath = target.getParent
    if (!Files.exists(parentPath)) {
      Files.createDirectories(parentPath)
    }
    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
  }

  override def moveFolder(oldPath: Path, newPath: Path): F[Path] = Sync[F].delay {
    Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE)
  }

  override def removeFolder(path: Path): F[Unit] = {
    getFileOrFail(path)
      .map(ApacheFS.deleteDirectory)
  }

  override def getTempDir(prefix: String): F[Path] =
    Sync[F].delay(Files.createTempDirectory(prefix))

  override def readText(path: Path): F[List[String]] = {
    getFileOrFail(path).map { x => Files.readAllLines(x.toPath).asScala.toList }
  }

  override def readBytes(path: Path): F[Array[Byte]] = {
    getFileOrFail(path).map(x => Files.readAllBytes(x.toPath))
  }

  override def writeBytes(path: Path, bytes: Array[Byte]): F[Path] = Sync[F].delay {
    Files.write(path, bytes)
  }
}

package io.hydrosphere.serving.manager.config

import java.nio.file.{Files, Path, Paths}
import cats.effect.Sync
import io.circe.generic.JsonCodec
import io.circe.parser._
import scala.util.Try
import io.hydrosphere.serving.manager.util.JsonOps._

@JsonCodec
case class DockerClientProxy(
  httpProxy: Option[String] = None,
  httpsProxy: Option[String] = None,
  ftpProxy: Option[String] = None,
  noProxy: Option[String] = None
)

//TODO proxies may be empty!
@JsonCodec
case class DockerClientConfig(
  proxies: Map[String, DockerClientProxy] = Map.empty
)

object DockerClientConfig {
  final val defaultConfigPath = Paths.get(System.getProperty("user.home"), ".docker/config.json")

  def load[F[_]](path: Path)(implicit F: Sync[F])= F.defer{
    val fileBytes = Files.readAllBytes(path)
    val fileContent = new String(fileBytes)
    F.fromEither(decode[DockerClientConfig](fileContent))
  }

  def empty: DockerClientConfig = {
    DockerClientConfig(Map.empty)
  }
}
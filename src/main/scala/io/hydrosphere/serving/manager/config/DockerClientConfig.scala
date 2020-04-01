package io.hydrosphere.serving.manager.config

import java.nio.file.{Files, Path, Paths}

import cats.effect.Sync

import scala.util.Try

case class DockerClientProxy(
  httpProxy: Option[String] = None,
  httpsProxy: Option[String] = None,
  ftpProxy: Option[String] = None,
  noProxy: Option[String] = None
)

//TODO proxies may be empty!
case class DockerClientConfig(
  proxies: Map[String, DockerClientProxy] = Map.empty
)

object DockerClientConfig {

  import io.hydrosphere.serving.manager.infrastructure.protocol.CommonJsonProtocol._
  import spray.json._

  implicit val proxyFormat = jsonFormat4(DockerClientProxy.apply)
  implicit val format = jsonFormat1(DockerClientConfig.apply)

  final val defaultConfigPath = Paths.get(System.getProperty("user.home"), ".docker/config.json")

  def load[F[_]](path: Path)(implicit F: Sync[F]): F[DockerClientConfig] = F.delay {
    val fileBytes = Files.readAllBytes(path)
    val fileContent = new String(fileBytes)
    fileContent.parseJson.convertTo[DockerClientConfig]
  }

  def empty: DockerClientConfig = {
    DockerClientConfig(Map.empty)
  }
}
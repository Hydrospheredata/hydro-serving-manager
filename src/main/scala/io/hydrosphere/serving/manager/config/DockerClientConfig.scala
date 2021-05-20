package io.hydrosphere.serving.manager.config

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.JsonCodec
import io.circe.parser._

import java.nio.file.{Files, Path, Paths}

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

  def load[F[_]](path: Path)(implicit F: Sync[F]): F[DockerClientConfig] =
    F.blocking(new String(Files.readAllBytes(path)))
      .flatMap(x => F.fromEither(decode[DockerClientConfig](x)))

  def empty: DockerClientConfig =
    DockerClientConfig(Map.empty)
}

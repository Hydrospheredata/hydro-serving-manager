package io.hydrosphere.serving.manager.infrastructure.docker

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import cats.effect.Async
import cats.implicits._
import com.spotify.docker.client.DockerClient.{
  BuildParam,
  EventsParam,
  ListContainersParam,
  RemoveContainerParam
}
import com.spotify.docker.client.messages._
import com.spotify.docker.client.{DefaultDockerClient, DockerClient, ProgressHandler}
import io.hydrosphere.serving.manager.config.DockerClientConfig
import org.apache.logging.log4j.scala.Logging
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstanceEvent
import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstanceEventAdapterInstances._

import scala.collection.JavaConverters._

trait DockerdClient[F[_]] {

  def createContainer(container: ContainerConfig, name: Option[String]): F[ContainerCreation]

  def runContainer(id: String): F[Unit]

  def removeContainer(id: String, params: List[RemoveContainerParam]): F[Unit]
  def removeContainer(id: String): F[Unit] = removeContainer(id, Nil)

  def listContainers(params: List[ListContainersParam]): F[List[Container]]
  def listRunningContainers: F[List[Container]] = listContainers(Nil)
  def listAllContainers: F[List[Container]] =
    listContainers(ListContainersParam.allContainers() :: Nil)

  def logs(id: String, follow: Boolean): fs2.Stream[F, String]
  def events(): fs2.Stream[F, CloudInstanceEvent]

  def build(
      directory: Path,
      name: String,
      dockerfile: String,
      handler: ProgressHandler,
      params: List[BuildParam]
  ): F[String]

  def push(image: String, progressHandler: ProgressHandler, registryAuth: RegistryAuth): F[Unit]

  def inspectImage(image: String): F[ImageInfo]

  def getHost: F[String]
}

object DockerdClient extends Logging {

  case class DockerdClientException(error: String) extends Exception(error)

  def fromEnv[F[_]](implicit F: Async[F]): F[DockerdClient[F]] =
    F.delay(DefaultDockerClient.fromEnv().build())
      .flatMap(DockerdClient.create[F])

  def create[F[_]](underlying: DockerClient)(implicit F: Async[F]): F[DockerdClient[F]] =
    for {
      dockerClientConfig <-
        DockerClientConfig
          .load[F](DockerClientConfig.defaultConfigPath)
          .recover { case _ => DockerClientConfig() }
    } yield new DockerdClient[F] {

      override def createContainer(
          container: ContainerConfig,
          name: Option[String]
      ): F[ContainerCreation] =
        F.delay {
          name match {
            case Some(n) => underlying.createContainer(container, n)
            case None    => underlying.createContainer(container)
          }
        }

      override def runContainer(id: String): F[Unit] =
        F.delay(underlying.startContainer(id))

      override def removeContainer(id: String, params: List[RemoveContainerParam]): F[Unit] =
        F.delay(underlying.removeContainer(id, params: _*))

      override def listContainers(params: List[ListContainersParam]): F[List[Container]] =
        F.delay(underlying.listContainers(params: _*)).map(_.asScala.toList)

      override def logs(id: String, follow: Boolean): fs2.Stream[F, String] = {
        val rawStream =
          if (follow)
            F.delay(
              underlying.logs(
                id,
                DockerClient.LogsParam.stderr(),
                DockerClient.LogsParam.stdout(),
                DockerClient.LogsParam.follow()
              )
            )
          else
            F.delay(
              underlying.logs(id, DockerClient.LogsParam.stderr(), DockerClient.LogsParam.stdout())
            )
        fs2.Stream
          .eval(rawStream)
          .flatMap(x => fs2.Stream.fromIterator[F](x.asScala))
          .map(logMessage => StandardCharsets.UTF_8.decode(logMessage.content()).toString)
      }

      override def push(
          image: String,
          progressHandler: ProgressHandler,
          registryAuth: RegistryAuth
      ): F[Unit] =
        F.async { cb =>
          logger.debug(s"[DockerdClient] push image $image, $registryAuth")
          val internalProgressHandler = DockerdClient.asyncProgressHandler(progressHandler, cb)
          underlying.push(image, internalProgressHandler, registryAuth)
          cb(().asRight)
        }

      override def build(
          directory: Path,
          name: String,
          dockerfile: String,
          handler: ProgressHandler,
          params: List[BuildParam]
      ): F[String] =
        F.asyncF { cb =>
          logger.debug(s"[DockerdClient] build image $directory, $name, $dockerfile, $params")
          val internalProgressHandler = DockerdClient.asyncProgressHandler(handler, cb)
          proxyBuildParams.map { proxyParams =>
            val fullParams = proxyParams ++ params
            Option(
              underlying.build(directory, name, dockerfile, internalProgressHandler, fullParams: _*)
            ) match {
              case Some(value) => cb(value.asRight)
              case None        => cb(DockerdClientException("Can't build docker container").asLeft)
            }
          }
        }

      override def inspectImage(image: String): F[ImageInfo] =
        F.delay {
          underlying.inspectImage(image)
        }

      override def getHost: F[String] =
        F.delay {
          underlying.getHost
        }

      def proxyBuildParams: F[List[BuildParam]] =
        getHost.map { host =>
          dockerClientConfig.proxies
            .get(host)
            .orElse(dockerClientConfig.proxies.get("default"))
            .map { config =>
              val paramMap = List(
                config.httpProxy.map(x => "HTTP_PROXY" -> x),
                config.httpsProxy.map(x => "HTTPS_PROXY" -> x),
                config.noProxy.map(x => "NO_PROXY" -> x),
                config.ftpProxy.map(x => "FTP_PROXY" -> x)
              ).flatten.toMap
              BuildParam.create(
                "buildargs",
                URLEncoder.encode(paramMap.asJson.noSpaces, "UTF-8")
              )
            }
            .toList
        }

      override def events(): fs2.Stream[F, CloudInstanceEvent] = {
        val rawStream =
          F.delay(
            underlying.events(
              EventsParam.event("create"),
              EventsParam.event("start"),
              EventsParam.event("stop"),
              EventsParam.event("health_status"),
              EventsParam.`type`("container"),
              EventsParam.label("HS_INSTANCE_NAME"),
              EventsParam.label("HS_INSTANCE_MV_ID")
            )
          )
        fs2.Stream
          .eval(rawStream)
          .map(_.asScala)
          .flatMap(x => fs2.Stream.fromIterator[F](x))
          .map(_.toEvent)
          .filter(_.isRight)
          .map { case Right(value) => value }
      }
    }

  def asyncProgressHandler[T](
      childHandler: ProgressHandler,
      callback: Either[Throwable, T] => Unit
  ): ProgressHandler = {
    logger.debug("[DockerdClient] Created async progress handler")
    (message: ProgressMessage) => {
      logger.debug(s"[DockerdClient] handling $message")
      childHandler.progress(message) // call user-provided handler

      Option(message.error()).foreach { error => // handle error in logstream
        logger.debug(s"[DockerdClient] Caught error. Failing...")
        callback(DockerdClientException(error).asLeft)
      }
    }
  }
}

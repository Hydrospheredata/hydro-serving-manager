package io.hydrosphere.serving.manager.config

import cats.Show
import cats.derived.semiauto

sealed trait CloudDriverConfiguration {
  def loggingConfiguration: Option[ModelLoggingConfiguration]
}

object CloudDriverConfiguration {
  case class Docker(
      networkName: String,
      loggingConfiguration: Option[ModelLoggingConfiguration]
  ) extends CloudDriverConfiguration

  case class Kubernetes(
      proxyHost: String,
      proxyPort: Int,
      kubeNamespace: String,
      kubeRegistrySecretName: String,
      loggingConfiguration: Option[ModelLoggingConfiguration]
  ) extends CloudDriverConfiguration

  implicit val dockerShow: Show[Docker]           = semiauto.show
  implicit val localShow: Show[Kubernetes]        = semiauto.show
  implicit val cd: Show[CloudDriverConfiguration] = semiauto.show
}

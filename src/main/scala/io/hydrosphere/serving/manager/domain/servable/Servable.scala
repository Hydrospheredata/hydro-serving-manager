package io.hydrosphere.serving.manager.domain.servable

import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable.Status

// TODO: port/host
@JsonCodec
case class Servable(
    modelVersion: ModelVersion.Internal,
    nameSuffix: String,
    status: Status,
    message: String,
    host: Option[String],
    port: Option[Int],
    usedApps: List[String] = Nil,
    metadata: Map[String, String] = Map.empty,
    deploymentConfiguration: Option[DeploymentConfiguration] = None
) {
  def fullName: String =
    Servable.fullName(modelVersion.model.name, modelVersion.modelVersion, nameSuffix)
}

object Servable {

  sealed trait Status extends EnumEntry with Product
  case object Status extends Enum[Status] with CirceEnum[Status] {
    final case object Serving      extends Status
    final case object NotServing   extends Status
    final case object NotAvailable extends Status
    final case object Starting     extends Status

    override def values: IndexedSeq[Status] = findValues
  }

  def fullName(modelName: String, modelVersion: Long, suffix: String): String =
    s"$modelName-$modelVersion-$suffix".replace("_", "-")

  def extractSuffix(modelName: String, modelVersion: Long, name: String): String =
    name.replaceFirst(s"${modelName.replace("_", "-")}-$modelVersion-", "")

}

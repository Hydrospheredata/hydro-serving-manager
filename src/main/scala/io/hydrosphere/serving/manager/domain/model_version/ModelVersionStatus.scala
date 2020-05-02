package io.hydrosphere.serving.manager.domain.model_version

import cats.implicits._
import io.circe.{Decoder, Encoder}

sealed trait ModelVersionStatus extends Product with Serializable

object ModelVersionStatus {

  final case object Assembling extends ModelVersionStatus

  final case object Released extends ModelVersionStatus

  final case object Failed extends ModelVersionStatus

  implicit val decoder: Decoder[ModelVersionStatus] = Decoder.decodeString.emap {
    case "Assembling" => Assembling.asRight
    case "Released" => Released.asRight
    case "Failed" => Failed.asRight
    case x => s"Unknown status ${x}".asLeft
  }
  implicit val encoder: Encoder[ModelVersionStatus] = Encoder.encodeString.contramap(_.productPrefix)
}
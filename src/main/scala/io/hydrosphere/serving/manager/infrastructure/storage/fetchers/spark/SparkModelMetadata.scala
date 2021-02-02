package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Json}

@JsonCodec
case class SparkModelMetadata(
    `class`: String,
    timestamp: Long,
    sparkVersion: String,
    uid: String,
    paramMap: Map[String, Json],
    numFeatures: Option[Int],
    numClasses: Option[Int],
    numTrees: Option[Int]
) {
  def getParam[T: Decoder](param: String): Option[T] =
    paramMap.get(param).flatMap(_.as[T].toOption)

  val toMap: Map[String, String] = {
    val basic = Map(
      "sparkml.class"        -> `class`,
      "sparkml.timestamp"    -> timestamp.toString,
      "sparkml.sparkVersion" -> sparkVersion,
      "sparkml.uid"          -> uid
    )
    val opts = Map(
      "sparkml.numFeatures" -> numFeatures,
      "sparkml.numClasses"  -> numClasses,
      "sparkml.numTrees"    -> numTrees
    ).collect { case (k, Some(v)) => k -> v.toString }

    basic ++ opts
  }
}

object SparkModelMetadata {

  def extractParams(sparkMetadata: SparkModelMetadata, params: Seq[String]): Seq[String] =
    params.map(sparkMetadata.paramMap.get).filter(_.isDefined).map(_.get.asInstanceOf[String])
}

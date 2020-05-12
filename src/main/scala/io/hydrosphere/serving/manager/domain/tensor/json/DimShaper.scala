package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.manager.domain.contract.TensorShape.{Dynamic, Static}

sealed trait ColumnShaper {
  def shape(data: Seq[Json]): Json
}

case object AnyShaper extends ColumnShaper {
  override def shape(data: Seq[Json]): Json =
    data.toVector.asJson
}

case object ScalarShaper extends ColumnShaper {
  override def shape(data: Seq[Json]): Json =
    data.headOption.getOrElse(Map.empty[String, String].asJson)
}

case class DimShaper(dims: Seq[Long]) extends ColumnShaper {
  val strides: Seq[Long] = {
    val res   = Array.fill(dims.length)(1L)
    val stLen = dims.length - 1
    for (i <- 0.until(stLen).reverse)
      res(i) = res(i + 1) * dims(i + 1)
    res.toSeq
  }

  def shape(data: Seq[Json]): Json = {
    def shapeGrouped(dataId: Int, shapeId: Int): Json =
      if (shapeId >= dims.length)
        data(dataId)
      else {
        val n       = dims(shapeId).toInt
        val stride  = strides(shapeId).toInt
        var mDataId = dataId
        val res     = new Array[Json](n)

        for (i <- 0.until(n)) {
          val item = shapeGrouped(mDataId, shapeId + 1)
          res(i) = item
          mDataId += stride
        }
        res.toVector.asJson
      }
    // def shapeGrouped
    if (data.isEmpty)
      List.empty[String].asJson
    else
      shapeGrouped(0, 0)
  }
}

object ColumnShaper {
  def apply(tensorShape: TensorShape): ColumnShaper =
    tensorShape match {
      case Dynamic                      => AnyShaper
      case Static(dims) if dims.isEmpty => ScalarShaper
      case Static(dims)                 => DimShaper(dims)
    }
}

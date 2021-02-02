package io.hydrosphere.serving.manager.infrastructure.protocol

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import play.api.libs.json.{
  JsArray,
  JsBoolean,
  JsNull,
  JsNumber,
  JsObject,
  JsString,
  JsValue,
  Reads,
  Writes
}
import io.circe.syntax._
import cats.implicits._

object PlayJsonAdapter {
  def circeToPlay(x: Json): JsValue =
    x.fold[JsValue](
      JsNull,
      JsBoolean,
      jsonNumber => JsNumber(jsonNumber.toBigDecimal.get),
      JsString,
      jsonArray => JsArray(jsonArray.map(circeToPlay)),
      jsObject => JsObject(jsObject.toMap.map(v => v._1 -> circeToPlay(v._2)))
    )

  def playToCirce(p: JsValue): Json =
    p match {
      case JsNumber(i)   => i.asJson
      case JsString(s)   => s.asJson
      case JsBoolean(b)  => b.asJson
      case JsNull        => Json.Null
      case JsArray(arr)  => arr.map(playToCirce).asJson
      case JsObject(obj) => obj.view.mapValues(playToCirce).toMap.asJson
    }

  implicit def writeAdapter[T](v: T)(implicit W: Writes[T]): Json = playToCirce(W.writes(v))
  implicit def readAdapter[T](json: Json)(implicit R: Reads[T]): Result[T] =
    R.reads(circeToPlay(json)).asEither.leftMap { x =>
      val error = x.flatMap(_._2.map(_.message)).mkString
      DecodingFailure(error, Nil)
    }

  implicit def encoder[T](implicit W: Writes[T]) =
    new Encoder[T] {
      override def apply(a: T): Json = writeAdapter(a)
    }
  implicit def decoder[T](implicit R: Reads[T]) =
    new Decoder[T] {
      override def apply(h: HCursor): Result[T] = readAdapter[T](h.value)
    }
}

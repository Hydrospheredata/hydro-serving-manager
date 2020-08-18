package io.hydrosphere.serving.manager.infrastructure.protocol

import play.api.libs.json.{JsError, JsSuccess, Reads, Writes, JsArray => PJArray, JsBoolean => PJBoolean, JsNull => PJNull, JsNumber => PJNumber, JsObject => PJObject, JsString => PJString, JsValue => PJValue}
import spray.json.{DeserializationException, JsonReader, JsonWriter, RootJsonFormat, JsArray => SJArray, JsBoolean => SJBoolean, JsNull => SJNull, JsNumber => SJNumber, JsObject => SJObject, JsString => SJString, JsValue => SJValue}

object PlayJsonAdapter {
  def sprayJsonAdapter(json: SJValue): PJValue = {
    json match {
      case SJObject(value) => PJObject(value.map { case (k, v) => k -> sprayJsonAdapter(v) })
      case SJArray(value) => PJArray(value.map(sprayJsonAdapter))
      case SJString(value) => PJString(value)
      case SJNumber(value) => PJNumber(value)
      case SJBoolean(value) => PJBoolean(value)
      case SJNull => PJNull
    }
  }

  def playJsonAdapter(json: PJValue): SJValue = {
    json match {
      case PJObject(value) => SJObject(value.map { case (k, v) => k -> playJsonAdapter(v) }.toMap)
      case PJArray(value) => SJArray(value.map(playJsonAdapter).toVector)
      case PJString(value) => SJString(value)
      case PJNumber(value) => SJNumber(value)
      case PJBoolean(value) => SJBoolean(value)
      case PJNull => SJNull
    }
  }

  def readAdapter[T](implicit reads: Reads[T]): JsonReader[T] = {
    (json: SJValue) =>
      reads.reads(sprayJsonAdapter(json)) match {
        case JsSuccess(value, _) => value
        case JsError(errors) => throw DeserializationException(errors.mkString)
      }
  }

  def writeAdapter[T](implicit writes: Writes[T]): JsonWriter[T] = {
    (obj: T) => playJsonAdapter(writes.writes(obj))
  }

  def formatAdapter[T](implicit reads: Reads[T], writes: Writes[T]): RootJsonFormat[T] = new RootJsonFormat[T] {
    override def read(json: SJValue): T = readAdapter[T].read(json)

    override def write(obj: T): SJValue = writeAdapter[T].write(obj)
  }
}
package io.hydrosphere.serving.manager.infrastructure.protocol

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.util.DeferredResult
import org.apache.logging.log4j.scala.Logging
import scalapb._
import spray.json._

import scala.language.reflectiveCalls
import scala.util.Try

trait CommonJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with Logging {
  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(any: Any): JsValue = any match {
      case n: Int => JsNumber(n)
      case n: Long => JsNumber(n)
      case n: Float => JsNumber(n)
      case n: Double => JsNumber(n)
      case n: BigDecimal => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean => JsBoolean(b)
      case list: List[_] => seqFormat[Any].write(list)
      case array: Array[_] => seqFormat[Any].write(array.toList)
      case map: Map[String, _]@unchecked => mapFormat[String, Any] write map
      case e => throw DeserializationException(e.toString)
    }

    def read(value: JsValue): Any = value match {
      case JsNumber(n) => n.toDouble
      case JsString(s) => s
      case JsBoolean(b) => b
      case _: JsArray => listFormat[Any].read(value)
      case _: JsObject => mapFormat[String, Any].read(value)
      case e => throw DeserializationException(e.toString)
    }
  }

  implicit val uuidFormat: RootJsonFormat[UUID] = new RootJsonFormat[UUID] {
    override def write(obj: UUID): JsValue = JsString(obj.toString)

    override def read(json: JsValue): UUID = {
      json match {
        case JsString(str) => UUID.fromString(str)
        case x => throw DeserializationException(s"Invalid JsValue for UUID. Expected string, got $x")
      }
    }
  }

  implicit val throwableWriter: RootJsonFormat[Throwable] = new RootJsonFormat[Throwable] {
    override def write(obj: Throwable): JsValue = JsString(obj.getMessage)

    override def read(json: JsValue): Throwable = throw DeserializationException("Can't deserealize exceptions")
  }

  implicit def nonEmptyListFormat[T: JsonFormat]: RootJsonFormat[NonEmptyList[T]] = new RootJsonFormat[NonEmptyList[T]] {
    override def read(json: JsValue): NonEmptyList[T] = {
      val list = json.convertTo[List[T]]
      NonEmptyList.fromList(list) match {
        case Some(r) => r
        case None => throw DeserializationException("An array is required to be non-empty")
      }
    }

    override def write(obj: NonEmptyList[T]): JsValue = {
      obj.toList.toJson
    }
  }

  implicit def enumFormat[T <: scala.Enumeration](enum: T) = new RootJsonFormat[T#Value] {
    override def write(obj: T#Value): JsValue = JsString(obj.toString)

    override def read(json: JsValue): T#Value = json match {
      case JsString(txt) => enum.withName(txt)
      case somethingElse => throw DeserializationException(s"Expected a value from enum $enum instead of $somethingElse")
    }
  }

  implicit def protoEnumFormat[T <: GeneratedEnum](enumCompanion: GeneratedEnumCompanion[T]) = new RootJsonFormat[T] {
    override def write(obj: T): JsValue = {
      JsString(obj.toString())
    }

    override def read(json: JsValue): T = {
      json match {
        case JsString(str) =>
          enumCompanion.fromName(str)
            .getOrElse(throw DeserializationException(s"$str is invalid $enumCompanion"))
        case x => throw DeserializationException(s"$x is not a correct $enumCompanion")
      }
    }
  }


  implicit val localDateTimeFormat = new JsonFormat[LocalDateTime] {
    def write(x: LocalDateTime) = JsString(DateTimeFormatter.ISO_DATE_TIME.format(x))

    def read(value: JsValue) = value match {
      case JsString(x) => LocalDateTime.parse(x, DateTimeFormatter.ISO_DATE_TIME)
      case x => throw new DeserializationException(s"Unexpected type ${x.getClass.getName} when trying to parse LocalDateTime")
    }
  }

  implicit val instantFormat = new JsonFormat[Instant] {
    def write(obj: Instant): JsValue = JsString(obj.toString)
    def read(json: JsValue): Instant = json match {
      case JsString(value) =>
        Try(Instant.parse(value))
          .orElse(Try(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)))
          .getOrElse(throw DeserializationException("Provided time is neither Instant nor LocalDateTime"))
      case x => throw DeserializationException(s"Unexpected JSON for java.time.Instant: ${x.getClass.getName()}")
    }
  }

  implicit def deferredResult[F[_], T: JsonFormat] = new RootJsonFormat[DeferredResult[F, T]] {
    override def write(obj: DeferredResult[F, T]): JsValue = {
      obj.started.toJson
    }

    override def read(json: JsValue): DeferredResult[F, T] = throw DeserializationException(s"Can't read Deferred from json: $json")
  }
}

object CommonJsonProtocol extends CommonJsonProtocol
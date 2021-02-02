package io.hydrosphere.serving.manager.infrastructure.db

import cats.Show
import doobie.util.meta.Meta
import doobie.util.{Get, Put}
import io.hydrosphere.serving.manager.domain.application.ApplicationGraph
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.circe.{Decoder, Encoder, Json}
import org.postgresql.util.PGobject
import io.circe.parser._
import io.circe.syntax._
import cats.implicits._
import scala.reflect.runtime.universe.TypeTag

object Metas {

  // TODO: AnyVal
  implicit class MetaTmapExt[T: Show](val meta: Meta[T]) {
    def temap[B](gf: T => Either[String, B], pf: B => T)(implicit
        tt: TypeTag[T],
        bt: TypeTag[B]
    ): Meta[B] =
      new Meta[B](meta.get.temap(gf), meta.put.contramap(pf))
  }

  def jsonToPG(json: Json): PGobject = {
    val o = new PGobject
    o.setType("json")
    o.setValue(json.noSpaces)
    o
  }

  implicit val jsonMeta: Meta[Json] = {
    val pgMeta = Meta.Advanced.other[PGobject]("json")

    implicit val showPGObject: Show[PGobject] = (t: PGobject) => t.toString

    pgMeta.temap(
      obj => parse(obj.getValue).left.map(_.getMessage()),
      jsonToPG
    )
  }

  def jsonCodecMeta[T](implicit reader: Decoder[T], writer: Encoder[T], ev: TypeTag[T]): Meta[T] =
    jsonMeta.temap[T](_.as[T].leftMap(_.getMessage()), _.asJson)

  implicit val signaturePut: Put[Signature] = Put[String].contramap[Signature](_.asJson.noSpaces)
  implicit val signatureGet: Get[Signature] = Get[String].temap { str =>
    decode[Signature](str).leftMap(_.getMessage)
  }

  implicit val appGraphPut: Put[ApplicationGraph] = Put[String].contramap(_.asJson.noSpaces)
  implicit val appGraphGet: Get[ApplicationGraph] = Get[String].temap { str =>
    decode[ApplicationGraph](str).leftMap(_.getMessage)
  }

}

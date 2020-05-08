package io.hydrosphere.serving.manager.infrastructure.db

import doobie.util.{Get, Put}
import cats.implicits._
import io.circe.parser._
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.contract.{Contract, Signature}

object Metas {

  implicit val signaturePut: Put[Signature] = Put[String].contramap[Signature](_.asJson.noSpaces)
  implicit val signatureGet: Get[Signature] = Get[String].temap { str =>
    parse(str).flatMap(_.as[Signature]).leftMap(_.getMessage)
  }

  implicit val contractPut: Put[Contract] = Put[String].contramap[Contract](_.asJson.noSpaces)
  implicit val contractGet: Get[Contract] = Get[String].temap { str =>
    parse(str).flatMap(_.as[Contract]).leftMap(_.getMessage)
  }

}

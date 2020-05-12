package io.hydrosphere.serving.manager.infrastructure.db

import doobie.util.{Get, Put}
import cats.implicits._
import io.circe.parser._
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.application.ApplicationGraph
import io.hydrosphere.serving.manager.domain.contract.{Contract, Signature}
import io.hydrosphere.serving.manager.util.JsonOps._

object Metas {

  implicit val signaturePut: Put[Signature] = Put[String].contramap[Signature](_.asJson.noSpaces)
  implicit val signatureGet: Get[Signature] = Get[String].temap { str =>
    str.parseJsonAs[Signature].leftMap(_.getMessage)
  }

  implicit val contractPut: Put[Contract] = Put[String].contramap[Contract](_.asJson.noSpaces)
  implicit val contractGet: Get[Contract] = Get[String].temap { str =>
    str.parseJsonAs[Contract].leftMap(_.getMessage)
  }

  implicit val appGraphPut: Put[ApplicationGraph] = Put[String].contramap(_.asJson.noSpaces)
  implicit val appGraphGet: Get[ApplicationGraph] = Get[String].temap { str =>
    str.parseJsonAs[ApplicationGraph].leftMap(_.getMessage)
  }

}

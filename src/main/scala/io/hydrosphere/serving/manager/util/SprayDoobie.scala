package io.hydrosphere.serving.manager.util

import cats.data.NonEmptyList
import doobie.{Get, Put}
import org.postgresql.util.PGobject
import spray.json._


object SprayDoobie {
  implicit val jsonGet: Get[JsValue] =
    Get.Advanced.other[PGobject](NonEmptyList.of("json")).tmap[JsValue] { o =>
      o.getValue.parseJson
    }

  implicit val jsonPut: Put[JsValue] =
    Put.Advanced.other[PGobject](NonEmptyList.of("json")).tcontramap[JsValue] { j =>
      val o = new PGobject
      o.setType("json")
      o.setValue(j.compactPrint)
      o
    }

}

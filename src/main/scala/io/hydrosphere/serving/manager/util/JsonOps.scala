package io.hydrosphere.serving.manager.util

import io.circe
import io.circe.Decoder
import io.circe.parser.parse

object JsonOps {

  implicit final class JsonExt(val str: String) extends AnyVal {

    /**
      * Shortcut for parse(...).flatMap(_.as[T])
      */
    def parseJsonAs[T: Decoder]: Either[circe.Error, T] = parse(str).flatMap(_.as[T])
  }

}

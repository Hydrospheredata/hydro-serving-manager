package io.hydrosphere.serving.manager.util

import io.circe
import io.circe.{Decoder, Json, ParsingFailure}

object JsonOps {

  implicit final class JsonExt(val str: String) extends AnyVal {

    def parse: Either[ParsingFailure, Json] = io.circe.parser.parse(str)

    /**
      * Shortcut for parse(...).flatMap(_.as[T])
      */
    def parseJsonAs[T: Decoder]: Either[circe.Error, T] = str.parse.flatMap(_.as[T])
  }

}

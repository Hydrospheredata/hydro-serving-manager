package io.hydrosphere.serving.manager.util.random

import cats.Monad
import cats.implicits._

trait NameGenerator[F[_]] {
  def getName(): F[String]
}

object NameGenerator {
  def haiku[F[_]: Monad: RNG]() = {

    // full dictionaries at https://github.com/bmarcot/haiku
    val adjs = List("autumn", "hidden", "bitter", "misty", "silent",
      "reckless", "daunting", "short", "rising", "strong", "timber", "tumbling",
      "silver", "dusty", "celestial", "cosmic", "crescent", "double", "far",
      "terrestrial", "huge", "deep", "epic", "titanic", "mighty", "powerful")

    val nouns = List("waterfall", "river", "breeze", "moon", "rain",
      "wind", "sea", "morning", "snow", "lake", "sunset", "pine", "shadow", "leaf",
      "sequoia", "cedar", "wrath", "blessing", "spirit", "nova", "storm", "burst",
      "giant", "elemental", "throne", "game", "weed", "stone", "apogee", "bang")

    new NameGenerator[F] {
      override def getName(): F[String] = {
        for {
          adj <- RNG[F].getItem(adjs)
          noun <- RNG[F].getItem(nouns)
        } yield s"$adj-$noun"
      }
    }
  }
}

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
      "empty", "dry", "dark", "summer", "icy", "delicate", "quiet", "white", "cool",
      "spring", "winter", "patient", "twilight", "dawn", "crimson", "wispy",
      "weathered", "blue", "billowing", "broken", "cold", "damp", "falling",
      "frosty", "green", "long", "late", "lingering", "bold", "little", "morning",
      "muddy", "old", "red", "rough", "still", "small", "sparkling", "throbbing",
      "shy", "wandering", "withered", "wild", "black", "holy", "solitary",
      "fragrant", "aged", "snowy", "proud", "floral", "restless", "divine",
      "polished", "purple", "lively", "nameless", "puffy", "fluffy",
      "calm", "young", "golden", "avenging", "ancestral", "ancient", "argent",
      "reckless", "daunting", "short", "rising", "strong", "timber", "tumbling",
      "silver", "dusty", "celestial", "cosmic", "crescent", "double", "far", "half",
      "inner", "milky", "northern", "southern", "eastern", "western", "outer",
      "terrestrial", "huge", "deep", "epic", "titanic", "mighty", "powerful")


    val nouns = List("waterfall", "river", "breeze", "moon", "rain",
      "wind", "sea", "morning", "snow", "lake", "sunset", "pine", "shadow", "leaf",
      "dawn", "glitter", "forest", "hill", "cloud", "meadow", "glade",
      "bird", "brook", "butterfly", "bush", "dew", "dust", "field",
      "flower", "firefly", "feather", "grass", "haze", "mountain", "night", "pond",
      "darkness", "snowflake", "silence", "sound", "sky", "shape", "surf",
      "thunder", "violet", "wildflower", "wave", "water", "resonance",
      "sun", "wood", "dream", "cherry", "tree", "fog", "frost", "voice", "paper",
      "frog", "smoke", "star", "sierra", "castle", "fortress", "tiger", "day",
      "sequoia", "cedar", "wrath", "blessing", "spirit", "nova", "storm", "burst",
      "protector", "drake", "dragon", "knight", "fire", "king", "jungle", "queen",
      "giant", "elemental", "throne", "game", "weed", "stone", "apogee", "bang",
      "cluster", "corona", "cosmos", "equinox", "horizon", "light", "nebula",
      "solstice", "spectrum", "universe", "magnitude", "parallax")

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

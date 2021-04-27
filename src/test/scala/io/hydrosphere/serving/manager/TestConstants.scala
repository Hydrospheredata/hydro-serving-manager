package io.hydrosphere.serving.manager

import java.nio.file.{Path, Paths}

object TestConstants {
  val localModelsPath: Path =
    Paths.get(this.getClass.getClassLoader.getResource("test_models").toURI)
}

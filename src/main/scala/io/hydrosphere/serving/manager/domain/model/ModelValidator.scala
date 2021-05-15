package io.hydrosphere.serving.manager.domain.model

object ModelValidator {
  def name(name: String): Option[String] = {
    val validName = raw"^[a-z\-_\d]+$$".r
    if (validName.pattern.matcher(name).matches())
      Some(name)
    else
      None
  }
}

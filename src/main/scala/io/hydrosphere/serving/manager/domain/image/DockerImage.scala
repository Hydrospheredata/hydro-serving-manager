package io.hydrosphere.serving.manager.domain.image

import cats.syntax.either._
import io.circe.generic.JsonCodec

import scala.util.matching.Regex

@JsonCodec
case class DockerImage(
    name: String,
    tag: String,
    user: Option[String] = None,
    digest: Option[String] = None
) {

  lazy val fullName: String = {
    val sb = new StringBuilder()
    user.foreach(x => sb.append(x).append("/"))
    sb.append(name)
    sb.append(":")
    sb.append(tag)
    sb.toString()
  }
}

object DockerImage {
  val TagPattern: Regex    = "([a-zA-z0-9\\-_]+):([a-zA-z0-9\\-_]+)".r
  val DigestPattern: Regex = "([a-zA-z0-9\\-_]+)@sha256:([a-zA-z0-9]+)".r

  case class InvalidDockerImageName(msg: String) extends Exception(msg)

  final val dummyImage = DockerImage(
    user = Some("hydrosphere"),
    name = "serving-runtime-dummy",
    tag = "latest"
  )

  def parse(image: String): Either[InvalidDockerImageName, DockerImage] =
    for {
      (user, rest) <- parseUser(image)
      (name, ref)  <- parseName(rest)
    } yield DockerImage(
      user = user,
      name = name,
      tag = ref
    )

  def parseName(str: String): Either[InvalidDockerImageName, (String, String)] =
    str match {
      case TagPattern(name, tag) => (name -> tag).asRight
      case x                     => InvalidDockerImageName(s"Invalid docker tag specifier: $str").asLeft
    }

  def parseUser(input: String): Either[InvalidDockerImageName, (Option[String], String)] =
    input.split("/").toList match {
      case user :: rest :: Nil if user.nonEmpty && rest.nonEmpty => (Some(user) -> rest).asRight
      case rest :: Nil                                           => (None       -> rest).asRight
      case _                                                     => InvalidDockerImageName(s"Image name is invalid: $input").asLeft
      case Nil                                                   => InvalidDockerImageName(s"Image name cannot be empty").asLeft
    }
}

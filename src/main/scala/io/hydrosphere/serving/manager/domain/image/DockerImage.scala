package io.hydrosphere.serving.manager.domain.image

import cats.syntax.either._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.image.DockerImage.ImageTag.{Full, Sha256Digest, Tag}

import scala.util.matching.Regex

@JsonCodec
case class DockerImage(
    name: String,
    tag: DockerImage.ImageTag,
    user: Option[String] = None
) {
  def resolve(digest: String): DockerImage = {
    val newTag = tag match {
      case Tag(value)      => Full(digest, value)
      case Sha256Digest(_) => Sha256Digest(digest)
      case Full(_, tag)    => Full(digest, tag)
    }
    copy(tag = newTag)
  }

  lazy val fullName: String = {
    val sb = new StringBuilder()
    user.foreach(x => sb.append(x).append("/"))
    sb.append(name)
    tag match {
      case Tag(value)          => sb.append(":").append(value)
      case Sha256Digest(value) => sb.append("@sha256:").append(value)
      case Full(_, tag)        => sb.append(":").append(tag)
    }
    sb.toString()
  }
}

object DockerImage {
  val TagPattern: Regex    = "([a-zA-z0-9\\-_]+):([a-zA-z0-9\\-_]+)".r
  val DigestPattern: Regex = "([a-zA-z0-9\\-_]+)@sha256:([a-zA-z0-9]+)".r

  sealed trait ImageTag

  case object ImageTag {

    final case class Tag(value: String) extends ImageTag

    final case class Sha256Digest(value: String) extends ImageTag

    final case class Full(sha256Digest: String, tag: String) extends ImageTag

  }

  def tag(value: String)    = Tag(value)
  def sha256(value: String) = Sha256Digest(value)

  case class InvalidDockerImageName(msg: String) extends Exception(msg)

  final val dummyImage = DockerImage(
    user = Some("hydrosphere"),
    name = "serving-runtime-dummy",
    tag = ImageTag.Tag("latest")
  )

  def parse(image: String): Either[InvalidDockerImageName, DockerImage] = {
    for {
      (user, rest) <- parseUser(image)
      (name, ref)  <- parseName(rest)
    } yield DockerImage(
      user = user,
      name = name,
      tag = ref
    )
  }

  def parseName(str: String): Either[InvalidDockerImageName, (String, ImageTag)] = {
    str match {
      case DigestPattern(name, digest) => (name -> ImageTag.Sha256Digest(digest)).asRight
      case TagPattern(name, tag)       => (name -> ImageTag.Tag(tag)).asRight
      case x                           => InvalidDockerImageName(s"Invalid docker tag or digest specifier: $str").asLeft
    }
  }

  def parseUser(input: String): Either[InvalidDockerImageName, (Option[String], String)] = {
    input.split("/").toList match {
      case user :: rest :: Nil if user.nonEmpty && rest.nonEmpty => (Some(user) -> rest).asRight
      case rest :: Nil                                           => (None -> rest).asRight
      case _                                                     => InvalidDockerImageName(s"Image name is invalid: $input").asLeft
      case Nil                                                   => InvalidDockerImageName(s"Image name cannot be empty").asLeft
    }
  }
}

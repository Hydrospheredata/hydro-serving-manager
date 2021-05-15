package io.hydrosphere.serving.manager.domain.image

import cats.syntax.either._
import io.circe.generic.JsonCodec

import scala.util.matching.Regex

@JsonCodec
case class DockerImage(
    name: String,
    tag: String,
    sha256: Option[String] = None
) {

  def fullName: String = name + ":" + tag

  def replaceHost(host: String): Either[Throwable, DockerImage] =
    name match {
      case DockerImage.referenceRegexp(_, _, capturedName, _, _) =>
        DockerImage(s"$host/$capturedName", tag, sha256).asRight
      case x => new Exception(s"Can't parse image name $x").asLeft
    }
}

object DockerImage {
  // alphaNumericRegexp defines the alpha numeric atom, typically a
  // component of names. This only allows lower case characters and digits.
  val alphaNumericRegexp: Regex = "[a-z0-9]+".r

  // separatorRegexp defines the separators allowed to be embedded in name
  // components. This allow one period, one or two underscore and multiple
  // dashes.
  val separatorRegexp: Regex = "(?:[._]|__|[-]*)".r

  // nameComponentRegexp restricts registry path component names to start
  // with at least one letter or number, with following parts able to be
  // separated by one period, one or two underscore and multiple dashes.
  val nameComponentRegexp: Regex =
    expression(
      alphaNumericRegexp,
      optional(repeated(separatorRegexp, alphaNumericRegexp))
    )

  // domainComponentRegexp restricts the registry domain component of a
  // repository name to start with a component as defined by DomainRegexp
  // and followed by an optional port.
  val domainComponentRegexp: Regex = "(?:[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]|[a-zA-Z0-9])".r

  // domainRegexp defines the structure of potential domain components
  // that may be part of image names. This is purposely a subset of what is
  // allowed by DNS to ensure backwards compatibility with Docker image
  // names.
  val domainRegexp: Regex =
    expression(
      domainComponentRegexp,
      optional(repeated(literal("."), domainComponentRegexp)),
      optional(literal(":"), "[0-9]+".r)
    )

  // tagRegexp matches valid tag names. From docker/docker:graph/tags.go.
  val tagRegexp: Regex = "[\\w][\\w.-]{0,127}".r

  // digestRegexp matches valid digests.
  val digestRegexp: Regex =
    "[A-Za-z][A-Za-z0-9]*(?:[-_+.][A-Za-z][A-Za-z0-9]*)*[:][[:xdigit:]]{32,}".r

  // nameRegexp is the format for the name component of references. The
  // regexp has capturing groups for the domain and name part.
  val nameRegexp: Regex = new Regex(
    expression(
      optional(capture(domainRegexp)),
      literal("/"),
      capture(
        nameComponentRegexp,
        optional(repeated(literal("/"), nameComponentRegexp))
      )
    ).toString,
    "domain",
    "name"
  )

  // anchoredNameRegexp is used to parse a name value, capturing the
  // domain and trailing components.
  val anchoredNameRegexp: Regex = new Regex(
    expression(
      optional(capture(domainRegexp), literal("/")),
      capture(
        nameComponentRegexp,
        optional(repeated(literal("/"), nameComponentRegexp))
      )
    ).toString,
    "domain",
    "name"
  ).anchored

  // referenceRegexp is the full supported format of a reference. The regexp
  // is anchored and has capturing groups for name, tag, and digest
  // components.
  val referenceRegexp: Regex = new Regex(
    expression(
      capture(nameRegexp),
      optional(literal(":"), capture(tagRegexp)),
      optional(literal("@"), capture(digestRegexp))
    ).toString,
    "fullName",
    "domain",
    "name",
    "tag",
    "digest"
  )

  // identifierRegexp is the format for string identifier used as a
  // content addressable identifier using sha256. These identifiers
  // are like digests without the algorithm, since sha256 is used.
  val identifierRegexp: Regex = new Regex("([a-f0-9]{64})", "identifier")

  // ShortIdentifierRegexp is the format used to represent a prefix
  // of an identifier. A prefix may be used to match a sha256 identifier
  // within a list of trusted identifiers.
  val shortIdentifierRegexp: Regex = new Regex("([a-f0-9]{6,64})", "shortIdentifier")

  // literal compiles s into a literal regular expression, escaping any regexp
  // reserved characters
  def literal(s: String): Regex = Regex.quote(s).r

  // expression defines a full expression, where each regular expression must
  // follow the previous.
  def expression(res: Regex*): Regex = res.mkString("").r

  // repeated wraps the regexp in a non-capturing group to get one or more
  // matches.
  def repeated(res: Regex*): Regex = ("(?:" + expression(res: _*).toString + ")+").r

  // optional wraps the expression in a non-capturing group and makes the
  // production optional.
  def optional(res: Regex*): Regex = ("(?:" + expression(res: _*).toString + ")?").r

  // capture wraps the expression in a capturing group.
  def capture(res: Regex*): Regex = ("(" + expression(res: _*).toString + ")").r

  val dummyImage: DockerImage = DockerImage("hydrosphere/serving-runtime-dummy", "latest")
}

import sbt._

object Dependencies {
  val akkaVersion = "2.5.14"
  val akkaHttpVersion = "10.1.3"
  val log4j2Version = "2.8.2"
  val postgresqlVersion = "42.1.4"
  val scalaTestVersion = "3.0.3"
  val servingGrpcScala = "2.2.0"
  val catsV = "1.2.0"
  val envoyDataPlaneApi = "v1.6.0_1"

  val circe = Seq(
    "io.circe" %% "circe-core" % "0.12.1",
    "io.circe" %% "circe-generic" % "0.12.1",
    "io.circe" %% "circe-parser" % "0.12.1"
  )

  lazy val kubernetesDependencies = Seq(
    "io.skuber" %% "skuber" % "2.1.0"
  )

  lazy val akkaDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  )

  lazy val akkaHttpDependencies = Seq(
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "de.heikoseeberger" %% "akka-http-circe" % "1.29.1",
    "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.0.0" exclude("javax.ws.rs", "jsr311-api"),
    "ch.megard" %% "akka-http-cors" % "0.2.1"
  )

  lazy val grpcDependencies = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
    "io.hydrosphere" %% "serving-grpc-scala" % servingGrpcScala,
    "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion
  ).map(_ exclude("com.google.api.grpc", "proto-google-common-protos"))

  lazy val testDependencies = Seq(
    "org.mockito" % "mockito-all" % "1.10.19" % "test,it",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test,it",
    "com.dimafeng" %% "testcontainers-scala" % "0.7.0" % "test,it",
    "org.scalactic" %% "scalactic" % scalaTestVersion % "test,it",
    "org.scalatest" %% "scalatest" % scalaTestVersion % "test,it",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test,it",
    "com.amazonaws" % "aws-java-sdk-test-utils" % "1.11.174" % "test,it",
    "io.findify" %% "s3mock" % "0.2.3" % "test,it"
  )

  lazy val logDependencies = Seq(
    "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
    "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0"
  )

  lazy val mlDependencies = Seq(
    "org.bytedeco.javacpp-presets" % "hdf5-platform" % "1.10.2-1.4.2",
    "org.tensorflow" % "proto" % "1.10.0"
  )

  lazy val all = circe ++
    logDependencies ++
    akkaDependencies ++
    testDependencies ++
    akkaHttpDependencies ++
    grpcDependencies ++
    kubernetesDependencies ++
    mlDependencies ++
    Seq(
      "org.typelevel" %% "cats-effect" % catsV,
      "org.tpolecat" %% "doobie-core" % "0.7.0",
      "org.tpolecat" %% "doobie-postgres"  % "0.7.0",
      "com.zaxxer" % "HikariCP" % "2.6.3",  // doobie-hikari depends on hikari 3.3.1 which has weird pool retries.
      "org.tpolecat" %% "doobie-scalatest" % "0.7.0" % "test, it",
      "io.hydrosphere" %% "typed-sql" % "0.1.0",
      "org.postgresql" % "postgresql" % postgresqlVersion,
      "org.flywaydb" % "flyway-core" % "4.2.0",
      "com.spotify" % "docker-client" % "8.16.0" exclude("ch.qos.logback", "logback-classic"),
      "com.google.guava" % "guava" % "22.0",
      "com.github.pureconfig" %% "pureconfig" % "0.9.1",
      "co.fs2" %% "fs2-core" % "1.0.5",
      "co.fs2" %% "fs2-io" % "1.0.5",
      "com.github.krasserm" %% "streamz-converter" % "0.10-M2", // uses FS2 1.0.0,
      "com.github.mpilquist" %% "simulacrum" % "0.19.0"
    )
}
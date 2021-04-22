import sbt._

object Dependencies {
  val akkaVersion       = "2.6.9"
  val akkaHttpVersion   = "10.2.0"
  val log4j2Version     = "2.13.3"
  val postgresqlVersion = "42.2.4"
  val scalaTestVersion  = "3.2.2"
  val awsSdkVersion     = "1.11.312"
  val servingGrpcScala  = "3.0.0-dev3"
  val circeVersion      = "0.13.0"
  val catsV             = "2.2.0"
  val envoyDataPlaneApi = "v1.6.0_1"
  val fs2               = "2.4.4"
  val enumeratumV       = "1.6.0"
  val mockitoScalaV     = "1.16.3"
  val monocleV          = "3.0.0-M4"

  lazy val awsDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk-ecs"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-ec2"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-iam"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-ecr"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-sqs"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-s3"      % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-route53" % awsSdkVersion
  )

  lazy val kubernetesDependencies = Seq("io.skuber" %% "skuber" % "2.6.0")

  lazy val akkaDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor"  % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"  % akkaVersion
  )

  lazy val akkaHttpDependencies = Seq(
    "com.typesafe.akka"            %% "akka-http-core"    % akkaHttpVersion,
    "com.typesafe.akka"            %% "akka-http"         % akkaHttpVersion,
    "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.2.0" exclude ("javax.ws.rs", "jsr311-api"),
    "ch.megard"                    %% "akka-http-cors"    % "1.0.0"
  )

  lazy val grpcDependencies = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
    "io.hydrosphere"       %% "serving-grpc-scala"   % servingGrpcScala,
    "io.grpc"               % "grpc-netty"           % scalapb.compiler.Version.grpcJavaVersion
  ).map(_ exclude ("com.google.api.grpc", "proto-google-common-protos"))

  lazy val testDependencies = Seq(
//    "org.mockito"       %% "mockito-scala"           % mockitoScalaV    % "test,it",
//    "org.mockito"       %% "mockito-scala-scalatest" % mockitoScalaV    % "test,it",
//    "org.mockito"       %% "mockito-scala-cats"      % mockitoScalaV    % "test,it",
    "org.mockito"        % "mockito-all"             % "1.10.19"        % "test,it",
    "com.typesafe.akka" %% "akka-http-testkit"       % akkaHttpVersion  % "test,it",
    "org.scalactic"     %% "scalactic"               % scalaTestVersion % "test,it",
    "org.scalatest"     %% "scalatest"               % scalaTestVersion % "test,it",
    "com.typesafe.akka" %% "akka-testkit"            % akkaVersion      % "test,it",
    "com.amazonaws"      % "aws-java-sdk-test-utils" % "1.11.174"       % "test,it",
    "org.scalatestplus" %% "mockito-3-4"             % "3.2.2.0"        % "test"
  )

  lazy val logDependencies = Seq(
    "org.apache.logging.log4j"  % "log4j-api"        % log4j2Version,
    "org.apache.logging.log4j"  % "log4j-core"       % log4j2Version,
    "org.apache.logging.log4j"  % "log4j-slf4j-impl" % log4j2Version,
    "org.apache.logging.log4j" %% "log4j-api-scala"  % "12.0"
  )

  lazy val mlDependencies = Seq(
    "org.bytedeco.javacpp-presets" % "hdf5-platform" % "1.10.2-1.4.2",
    "org.tensorflow"               % "proto"         % "1.10.0"
  )

  lazy val circeDependencies = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-generic-extras"
  ).map(_ % circeVersion)
  lazy val akkaCirceDependencies = Seq("de.heikoseeberger" %% "akka-http-circe" % "1.31.0")
  lazy val enumeratumDependencies = Seq(
    "com.beachape" %% "enumeratum"       % enumeratumV,
    "com.beachape" %% "enumeratum-circe" % enumeratumV
  )

  lazy val all = logDependencies ++
    akkaDependencies ++
    testDependencies ++
    akkaHttpDependencies ++
    awsDependencies ++
    grpcDependencies ++
    kubernetesDependencies ++
    mlDependencies ++
    circeDependencies ++
    akkaCirceDependencies ++
    enumeratumDependencies ++
    Seq(
//      "com.github.julien-truffaut" %% "monocle-core"      % monocleV,
//      "com.github.julien-truffaut" %% "monocle-macro"     % monocleV, // only for Scala 2.13
      "org.typelevel"         %% "kittens"           % "2.2.1",
      "org.typelevel"         %% "cats-effect"       % catsV,
      "org.tpolecat"          %% "doobie-core"       % "0.9.0",
      "org.tpolecat"          %% "doobie-postgres"   % "0.9.0",
      "org.tpolecat"          %% "doobie-scalatest"  % "0.9.0" % "test, it",
      "com.zaxxer"             % "HikariCP"          % "2.6.3", // doobie-hikari depends on hikari 3.3.1 which has weird pool retries.
      "org.postgresql"         % "postgresql"        % postgresqlVersion,
      "org.flywaydb"           % "flyway-core"       % "4.2.0",
      "com.spotify"            % "docker-client"     % "8.16.0" exclude ("ch.qos.logback", "logback-classic"),
      "com.google.guava"       % "guava"             % "22.0",
      "com.github.pureconfig" %% "pureconfig"        % "0.14.0",
      "co.fs2"                %% "fs2-core"          % fs2,
      "co.fs2"                %% "fs2-io"            % fs2,
      "com.github.krasserm"   %% "streamz-converter" % "0.13-RC4", // uses FS2 2.x.x,
      "com.github.mpilquist"  %% "simulacrum"        % "0.19.0"
    )
}

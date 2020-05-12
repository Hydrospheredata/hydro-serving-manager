import sbt._

object Dependencies {
  lazy val akkaVersion       = "2.6.4"
  lazy val akkaHttpVersion   = "10.1.11"
  lazy val log4j2Version     = "2.8.2"
  lazy val postgresqlVersion = "42.1.4"
  lazy val scalaTestVersion  = "3.1.1"
  lazy val awsSdkVersion     = "1.11.312"
  lazy val servingGrpcScala  = "2.3.0-dev1"
  lazy val catsV             = "2.1.3"
  lazy val doobieV           = "0.9.0"
  lazy val circeVersion      = "0.13.0"
  lazy val enumeratumV       = "1.6.0"
  lazy val mockitoV          = "1.14.1"
  lazy val logstageVersion   = "0.10.7"
  lazy val distageVersion    = "0.10.7"

  lazy val aws = Seq(
    "com.amazonaws" % "aws-java-sdk-ecs"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-ec2"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-iam"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-ecr"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-sqs"     % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-s3"      % awsSdkVersion,
    "com.amazonaws" % "aws-java-sdk-route53" % awsSdkVersion
  )

  lazy val infrastructure = Seq(
    "io.skuber"  %% "skuber"        % "2.4.0",
    "com.spotify" % "docker-client" % "8.16.0" exclude ("ch.qos.logback", "logback-classic")
  )

  lazy val akks = Seq(
    "com.typesafe.akka" %% "akka-actor"  % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"  % akkaVersion
  )

  lazy val json = Seq(
    "io.circe"          %% "circe-core"           % circeVersion,
    "io.circe"          %% "circe-parser"         % circeVersion,
    "io.circe"          %% "circe-generic"        % circeVersion,
    "io.circe"          %% "circe-generic-extras" % circeVersion,
    "de.heikoseeberger" %% "akka-http-circe"      % "1.31.0"
  )

  lazy val akkaHttp = Seq(
    "com.typesafe.akka"            %% "akka-http-core"    % akkaHttpVersion,
    "com.typesafe.akka"            %% "akka-http"         % akkaHttpVersion,
    "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.0.5" exclude ("javax.ws.rs", "jsr311-api"),
    "ch.megard"                    %% "akka-http-cors"    % "0.4.3"
  )

  lazy val grpc = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
    "io.hydrosphere"       %% "serving-grpc-scala"   % servingGrpcScala,
    "io.grpc"               % "grpc-netty"           % scalapb.compiler.Version.grpcJavaVersion
  ).map(_ exclude ("com.google.api.grpc", "proto-google-common-protos"))

  lazy val test = Seq(
    "org.mockito"       %% "mockito-scala"           % mockitoV         % "test,it",
    "org.mockito"       %% "mockito-scala-scalatest" % mockitoV         % "test,it",
    "org.scalactic"     %% "scalactic"               % scalaTestVersion % "test,it",
    "org.scalatest"     %% "scalatest"               % scalaTestVersion % "test,it",
    "com.typesafe.akka" %% "akka-http-testkit"       % akkaHttpVersion  % "test,it",
    "com.typesafe.akka" %% "akka-testkit"            % akkaVersion      % "test,it"
  )

  lazy val logs = Seq(
    "org.apache.logging.log4j" % "log4j-api"                  % log4j2Version,
    "org.apache.logging.log4j" % "log4j-core"                 % log4j2Version,
    "org.apache.logging.log4j" % "log4j-slf4j-impl"           % log4j2Version,
    "io.7mind.izumi"          %% "logstage-core"              % logstageVersion,
    "io.7mind.izumi"          %% "distage-extension-logstage" % logstageVersion,
    "io.7mind.izumi"          %% "logstage-sink-slf4j"        % logstageVersion
  )

  lazy val ml = Seq(
    "org.bytedeco.javacpp-presets" % "hdf5-platform" % "1.10.2-1.4.2",
    "org.tensorflow"               % "proto"         % "1.10.0"
  )

  lazy val db = Seq(
    "org.tpolecat"  %% "doobie-core"      % doobieV,
    "org.tpolecat"  %% "doobie-postgres"  % doobieV,
    "com.zaxxer"     % "HikariCP"         % "2.6.3", // doobie-hikari depends on hikari 3.3.1 which has weird pool retries.
    "org.tpolecat"  %% "doobie-scalatest" % doobieV % "test, it",
    "org.postgresql" % "postgresql"       % postgresqlVersion,
    "org.flywaydb"   % "flyway-core"      % "4.2.0"
  )

  lazy val di = Seq(
    "io.7mind.izumi" %% "distage-core"              % distageVersion,
    "io.7mind.izumi" %% "distage-extension-config"  % distageVersion,
    "io.7mind.izumi" %% "distage-framework"         % distageVersion,
    "io.7mind.izumi" %% "distage-framework-docker"  % distageVersion,
    "io.7mind.izumi" %% "distage-testkit-scalatest" % distageVersion
  )

  lazy val all = logs ++
    akks ++
    test ++
    akkaHttp ++
    aws ++
    grpc ++
    infrastructure ++
    ml ++
    db ++
    json ++
    di ++
    Seq(
      "org.typelevel"         %% "cats-effect"       % catsV,
      "co.fs2"                %% "fs2-core"          % "2.3.0",
      "co.fs2"                %% "fs2-io"            % "2.3.0",
      "com.github.krasserm"   %% "streamz-converter" % "0.11-RC1",
      "org.typelevel"         %% "simulacrum"        % "1.0.0",
      "com.github.pureconfig" %% "pureconfig"        % "0.12.3",
      "com.beachape"          %% "enumeratum"        % enumeratumV,
      "com.beachape"          %% "enumeratum-circe"  % enumeratumV
    )
}

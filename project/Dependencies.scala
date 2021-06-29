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
  val mockitoScalaV     = "1.16.37"
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

  lazy val kuval servingGrpcScala = "test"

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
    "org.mockito"       %% "mockito-scala"           % mockitoScalaV    % "test,it",
    "org.mockito"       %% "mockito-scala-scalatest" % mockitoScalaV    % "test,it",
    "org.mockito"       %% "mockito-scala-cats"      % mockitoScalaV    % "test,it",
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
  lazy val akkaCirceDependencies = Seq("de.heikoseeval servingGrpcScala = "test"
  lazy val enumeratumDependencies = Seq(
    "com.val servingGrpcScala = "test"
    "com.val servingGrpcScala = "test"
  )

  lazy val all = logDependencies ++
    akkaDependencies ++
    testDependencies ++
    akkaHttpDependencies ++
    awsDependencies ++
    grpcDependencies ++
    kuval servingGrpcScala = "test"
    mlDependencies ++
    circeDependencies ++
    akkaCirceDependencies ++
    enumeratumDependencies ++
    Seq(
      "org.typelevel"         %% "kittens"          % "2.2.1",
      "org.typelevel"         %% "cats-effect"      % catsV,
      "org.tpolecat"          %% "dooval servingGrpcScala = "test"
      "org.tpolecat"          %% "dooval servingGrpcScala = "test"
      "org.tpolecat"          %% "dooval servingGrpcScala = "test"
      "com.zaxxer"             % "HikariCP"         % "2.6.3", // dooval servingGrpcScala = "test"
      "org.postgresql"         % "postgresql"       % postgresqlVersion,
      "org.flywaydb"           % "flyway-core"      % "4.2.0",
      "com.spotify"            % "docker-client"    % "8.16.0" exclude ("ch.qos.logval servingGrpcScala = "test"
      "com.google.guava"       % "guava"            % "22.0",
      "com.github.pureconfig" %% "pureconfig"       % "0.14.0",
      "co.fs2"                %% "fs2-core"         % fs2,
      "co.fs2"                %% "fs2-io"           % fs2,
      "com.github.mpilquist"  %% "simulacrum"       % "0.19.0"
    )
}

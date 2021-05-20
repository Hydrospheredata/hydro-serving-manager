name := "serving-manager"
version := sys.props.getOrElse("appVersion", IO.read(file("version")).trim)
organization := "io.hydrosphere.serving"
homepage := Some(url("https://hydrosphere.io/serving-docs"))

scalaVersion := "2.13.5"
scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ymacro-annotations",
  "-Xmaxerrs",
  "1000"
)

publishArtifact := false

Test / parallelExecution := false
IntegrationTest / parallelExecution := false

Test / test / fork := true
IntegrationTest / test / fork := true
IntegrationTest / testOnly / fork := true

enablePlugins(BuildInfoPlugin, DockerPlugin)
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

configs(IntegrationTest)
Defaults.itSettings

buildInfoKeys := Seq[BuildInfoKey](
  name,
  version,
  scalaVersion,
  sbtVersion,
  git.gitCurrentBranch,
  git.gitCurrentTags,
  git.gitHeadCommit
)
buildInfoPackage := "io.hydrosphere.serving"
buildInfoOptions += BuildInfoOption.ToJson

docker / imageNames := Seq(ImageName(s"hydrosphere/serving-manager:${version.value}"))
docker / dockerfile := {
  val jarFile: File       = (Compile / packageBin / sbt.Keys.`package`).value
  val classpath           = (Compile / dependencyClasspath).value
  val localConfigFile     = baseDirectory.value / "src" / "main" / "resources" / "application.conf"
  val dockerFilesLocation = baseDirectory.value / "src" / "main" / "docker/"
  val jarTarget           = "/app/manager.jar"
  val libFolder           = "/app/lib/"
  val defaultConfigPath   = "/app/config/application.conf"

  new sbtdocker.Dockerfile {
    // Base image
    from("openjdk:8u151-jre-alpine")

    run("apk", "update")
    run("apk", "add", "jq")
    run("rm", "-rf", "/var/cache/apk/*")

    add(dockerFilesLocation, "/app/")
    // Add all files on the classpath
    add(classpath.files, libFolder)
    // Add the JAR file
    add(jarFile, jarTarget)
    add(localConfigFile, defaultConfigPath)

    entryPointShell("/app/start.sh")
  }
}

resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/staging"

libraryDependencies ++= Dependencies.all

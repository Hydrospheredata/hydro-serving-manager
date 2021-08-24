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

parallelExecution in Test := false
parallelExecution in IntegrationTest := false

fork in (Test, test) := true
fork in (IntegrationTest, test) := true
fork in (IntegrationTest, testOnly) := true

enablePlugins(BuildInfoPlugin, DockerPlugin)

configs(IntegrationTest)
ManagerDev.settings
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

imageNames in docker := Seq(ImageName(s"hydrosphere/serving-manager:${version.value}"))
dockerfile in docker := {
  val jarFile: File       = (Compile / packageBin / sbt.Keys.`package`).value
  val classpath           = (Compile / dependencyClasspath).value
  val localConfigFile     = baseDirectory.value / "src" / "main" / "resources" / "application.conf"
  val dockerFilesLocation = baseDirectory.value / "src" / "main" / "docker/"
  val jarTarget           = "manager.jar"
  val libFolder           = "./lib/"
  val defaultConfigPath   = "./config/application.conf"

  new sbtdocker.Dockerfile {
    // Base image
    from("openjdk:17-ea-14-alpine3.13")

    label("maintainer", "support@hydrosphere.io")

    run("apk", "update")
    run("apk", "add", "apk-tools=2.12.7-r0", "jq")
    run("rm", "-rf", "/var/cache/apk/*")

    workDir("/app/")

    copy(dockerFilesLocation, "./")
    // Add all files on the classpath
    copy(classpath.files, libFolder)
    // Add the JAR file
    copy(jarFile, jarTarget)
    copy(localConfigFile, defaultConfigPath)
    
    entryPointShell("/app/start.sh")
  }
}

resolvers += Resolver.sonatypeRepo("public")
resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.bintrayRepo("streamz", "maven")
resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/staging"

libraryDependencies ++= Dependencies.all

lazy val root    = project.in(file(".")).dependsOn(streamz % "compile->compile")
lazy val streamz = ProjectRef(uri("https://github.com/krasserm/streamz.git"), "converter")

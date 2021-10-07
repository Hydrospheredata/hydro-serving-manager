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
    from("openjdk:8u212-jre-alpine3.9")

    label("maintainer", "support@hydrosphere.io")

    run("apk", "update")
    run("apk", "add", "--no-cache", "freetype>=2.9.1-r3", "krb5-libs>=1.15.5-r1", "libbz2>=1.0.6-r7", 
        "libcom_err>=1.44.5-r2", "libcrypto1.1>=1.1.1k-r0", "libjpeg-turbo>=1.5.3-r6", "libssl1.1>=1.1.1k-r0", 
        "libtasn1>=4.14", "libx11>=1.6.12-r0", "musl>=1.1.20-r6", "openjdk8-jre>=8.272.10-r0", 
        "openjdk8-jre-base>=8.272.10-r0", "openjdk8-jre-lib>=8.272.10-r0", "sqlite-libs>=3.28.0-r3", "jq")
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

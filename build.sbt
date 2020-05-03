name := "manager"
version := sys.props.getOrElse("appVersion", IO.read(file("version")).trim)
organization := "io.hydrosphere.serving"
homepage := Some(url("https://hydrosphere.io/serving-docs"))

scalaVersion := "2.13.2"
scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Ymacro-annotations"
)

publishArtifact := false

parallelExecution in Test := false
parallelExecution in IntegrationTest := false

fork in(Test, test) := true
fork in(IntegrationTest, test) := true
fork in(IntegrationTest, testOnly) := true

enablePlugins(BuildInfoPlugin, sbtdocker.DockerPlugin)

configs(IntegrationTest)
ManagerDev.settings
Defaults.itSettings
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitCurrentBranch, git.gitCurrentTags, git.gitHeadCommit)
buildInfoPackage := "io.hydrosphere.serving"
buildInfoOptions += BuildInfoOption.ToJson

imageNames in docker := Seq(ImageName(s"hydrosphere/serving-manager:${version.value}"))
dockerfile in docker := {
  val jarFile: File = sbt.Keys.`package`.in(Compile, packageBin).value
  val classpath = (dependencyClasspath in Compile).value
  val dockerFilesLocation = baseDirectory.value / "src/main/docker/"
  val jarTarget = s"/hydro-serving/app/manager.jar"
  val osName = sys.props.get("os.name").getOrElse("unknown")

  new sbtdocker.Dockerfile {
    // Base image
    from("openjdk:8u151-jre-alpine")

    run("apk", "update")
    run("apk", "add", "jq")
    run("rm", "-rf", "/var/cache/apk/*")

    add(dockerFilesLocation, "/hydro-serving/app/")
    // Add all files on the classpath
    add(classpath.files, "/hydro-serving/app/lib/")
    // Add the JAR file
    add(jarFile, jarTarget)

    cmd("/hydro-serving/app/start.sh")
  }
}
resolvers += "streamz at bintray" at "https://dl.bintray.com/streamz/maven"
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
libraryDependencies ++= Dependencies.all
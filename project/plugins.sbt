logLevel := Level.Info

resolvers += "Flyway" at "https://davidmweber.github.io/flyway-sbt.repo"

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.8.2")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage"   % "sbt-scoverage"         % "1.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-git"       % "1.0.0")
addSbtPlugin("com.thesamet"     % "sbt-protoc"    % "0.99.28")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"  % "0.9.21")

libraryDependencies ++= Seq(
  "com.spotify"           % "docker-client"  % "8.8.0",
  "org.flywaydb"          % "flyway-core"    % "4.2.0",
  "com.thesamet.scalapb" %% "compilerplugin" % "0.10.0"
)

import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerExposedPorts, dockerUsername}
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}

version := "1.0"

val akkaVersion = "2.5.26"
val circeVersion = "0.13.0"
val slf4jVersion = "1.7.25"
val elastic4sVersion = "6.5.1"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.13",
  scalacOptions +="-target:jvm-1.8",
  javacOptions ++= Seq("-source", "1.8","-target","1.8"),
  libraryDependencies ++= Seq( guice , ehcache, ws , specs2 % Test ),
  unmanagedResourceDirectories in Test +=  { baseDirectory ( _ /"target/web/public/test" ).value },
  unmanagedBase := baseDirectory.value / "lib",
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "com.dripower" %% "play-circe" % "2711.0",
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "commons-codec" % "commons-codec" % "1.13",
    "commons-io" % "commons-io" % "2.6",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.github.scopt" %% "scopt" % "3.7.1",
    "io.circe" %% "circe-yaml" % "0.13.0",
    "org.mongodb.scala" %% "mongo-scala-driver" % "2.6.0",
    "ai.snips" %% "play-mongo-bson" % "0.5.1",
    "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-http-streams" % elastic4sVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test",
    "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion % "test",
    "com.github.scredis" %% "scredis" % "2.3.3",
    "org.specs2" %% "specs2-core" % "4.5.1" % Test,
    "org.specs2" %% "specs2-mock" % "4.5.1" % Test,
    "org.mockito" % "mockito-core" % "2.28.2" % Test,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  ),
  //Sentry
  libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.2",
  //authentication
  libraryDependencies ++= Seq(
    "com.nimbusds" % "nimbus-jose-jwt" % "8.17",
  ),
  //snyk updates
  libraryDependencies ++= Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.10.8",
    "com.google.guava" % "guava" % "30.0-jre",
    "org.apache.httpcomponents" % "httpclient" % "4.5.13"
  ),
  dockerBaseImage := "openjdk:8-jdk-alpine",
  dockerRepository := Some("guardianmultimedia"),
  dockerExposedPorts := Seq(9000),
  dockerPermissionStrategy := DockerPermissionStrategy.Run,
  daemonUserUid in Docker := None,
  daemonUser in Docker := "daemon",
  dockerUsername  := sys.props.get("docker.username"),
  dockerCommands ++= Seq(
    Cmd("USER","root"), //fix the permissions in the built docker image
    Cmd("RUN", "chown daemon /opt/docker"),
    Cmd("RUN", "chmod u+w /opt/docker"),
    Cmd("RUN", "chmod -R a+x /opt/docker"),
    Cmd("USER", "daemon")
  )
)

lazy val `vaultdoor` = (project in file(".")).settings(
  commonSettings,
  name := "VaultDoor",
  packageName in Docker := "guardianmultimedia/vaultdoor",
  dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"vaultdoor",Some(sys.props.getOrElse("build.number","DEV"))),
).enablePlugins(PlayScala)

lazy val `duplicate-finder` = (project in file("duplicate-finder")).enablePlugins(DockerPlugin,AshScriptPlugin).dependsOn(vaultdoor).settings(
  commonSettings,
  name := "Duplicate Finder",
  mainClass in (Compile,run) := Some("Main"),
  packageName in Docker := "guardianmultimedia/vaultdoor-duplicate-finder",
  dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"vaultdoor-duplicate-finder",Some(sys.props.getOrElse("build.number","DEV"))),
)

enablePlugins(RpmPlugin, SystemdPlugin)
rpmVendor := "Andy Gallagher <andy.gallagher@theguardian.com>"
packageName in Rpm := "vaultdoor"
version in Rpm := "1.0"
rpmRelease := sys.props.getOrElse("build.number","DEV")
rpmUrl := Some("https://github.com/fredex42/vaultdoor")
rpmLicense := Some("GPLv2")

enablePlugins(DockerPlugin,AshScriptPlugin)
version := sys.props.getOrElse("build.number","DEV")
packageName := "vaultdoor"

import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerExposedPorts, dockerUsername}
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}

name := "VaultDoor"
 
version := "1.0" 
      
lazy val `vaultdoor` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      
scalaVersion := "2.12.3"


libraryDependencies ++= Seq( guice , ehcache, ws , specs2 % Test )

unmanagedResourceDirectories in Test +=  { baseDirectory ( _ /"target/web/public/test" ).value }

unmanagedBase := baseDirectory.value / "lib"

val akkaVersion = "2.5.22"
val circeVersion = "0.9.3"
val slf4jVersion = "1.7.25"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "com.dripower" %% "play-circe" % "2711.0",
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "commons-codec" % "commons-codec" % "1.12",
  "commons-io" % "commons-io" % "2.6",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.scopt" %% "scopt" % "3.7.1",
  "io.circe" %% "circe-yaml" % "0.10.0",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.6.0",
  "ai.snips" %% "play-mongo-bson" % "0.5.1",
  "org.specs2" %% "specs2-core" % "4.5.1" % Test,
  "org.specs2" %% "specs2-mock" % "4.5.1" % Test,
  "org.mockito" % "mockito-core" % "2.28.2" % Test,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
)

//Sentry
libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.2"

//authentication
libraryDependencies += "com.unboundid" % "unboundid-ldapsdk" % "4.0.5"


enablePlugins(DockerPlugin,AshScriptPlugin)
version := sys.props.getOrElse("build.number","DEV")
dockerPermissionStrategy := DockerPermissionStrategy.Run
daemonUserUid in Docker := None
daemonUser in Docker := "daemon"
dockerExposedPorts := Seq(9000)
dockerUsername  := sys.props.get("docker.username")
dockerRepository := Some("guardianmultimedia")
packageName in Docker := "guardianmultimedia/vaultdoor"
packageName := "vaultdoor"
dockerBaseImage := "openjdk:8-jdk-alpine"
dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"vaultdoor",Some(sys.props.getOrElse("build.number","DEV")))
dockerCommands ++= Seq(
  Cmd("USER","root"), //fix the permissions in the built docker image
  Cmd("RUN", "chown daemon /opt/docker"),
  Cmd("RUN", "chmod u+w /opt/docker"),
  Cmd("RUN", "chmod -R a+x /opt/docker"),
  Cmd("USER", "daemon")
)
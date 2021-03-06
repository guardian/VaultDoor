logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.22")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.6")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
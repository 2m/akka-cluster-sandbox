scalaVersion := "2.11.7"

val akkaVersion = "2.4.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-tools"    % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"          % akkaVersion % "test",
  "org.scalatest"     %% "scalatest"             % "2.2.4"     % "test"
)

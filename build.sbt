name := "statsd-client"

version := "0.1-SNAPSHOT"

organization := "com.github.lechglowiak"

scalaVersion := "2.11.4"
crossScalaVersions := Seq("2.10.4", "2.11.4")

libraryDependencies ++= {
  val akkaVersion       = "2.3.7"
  Seq(
    "com.typesafe.akka" %% "akka-actor"      % akkaVersion,
    "com.typesafe" %% "config" % "1.2.1"
  )
}


name := """crawler"""

version := "1.0"

scalaVersion := "2.11.7"

// to avoid java.lang.RuntimeException: deduplicate: different file contents found in the following: logback.xml
// ref: https://stackoverflow.com/questions/25144484/sbt-assembly-deduplication-found-error
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
assemblyOutputPath in assembly := file("./standalone.jar")

libraryDependencies ++= {
  val akkaStreamV = "2.5.4"
  Seq(
    // Akka
    "com.typesafe.akka" %% "akka-stream" % akkaStreamV,
    // Log
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "log4j" % "log4j" % "1.2.17",
    "org.apache.logging.log4j" % "log4j-api" % "2.9.1",
    // Elasticsearch
    "org.apache.lucene" % "lucene-core" % "3.6.0",
    "com.sksamuel.elastic4s" %% "elastic4s-http" % "5.4.2",
    // iService
    "com.yukihirai0505" % "iservice_2.11" % "2.1.2",
    // Joda
    "joda-time" % "joda-time" % "2.9.3",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
}

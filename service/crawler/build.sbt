
name := """crawler"""

version := "1.0"

scalaVersion := "2.11.7"

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
    "com.yukihirai0505" % "iservice_2.11" % "2.4.0",
    // Joda
    "joda-time" % "joda-time" % "2.9.3",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
}

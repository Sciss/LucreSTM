name := "lucrestm"

ideaProjectName := "LucreSTM"

version := "0.10-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "org.scala-tools" %% "scala-stm" % "0.4",
   "com.sleepycat" % "je" % "4.1.10"
//   "com.weiglewilczek.slf4s" %% "slf4s" % "1.0.7"
//   "de.sciss" %% "treetests" % "0.11-SNAPSHOT"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked", "-Xelide-below", "INFO" )



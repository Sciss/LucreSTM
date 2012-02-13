name := "lucrestm"

version := "0.20-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "org.scala-tools" %% "scala-stm" % "0.5",
   "com.sleepycat" % "je" % "5.0.34"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked", "-Xelide-below", "INFO" ) // elide debug logging!



name := "lucrestm"

version := "0.20-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

resolvers += "Oracle Repository" at "http://download.oracle.com/maven"

libraryDependencies ++= Seq(
   "org.scala-tools" %% "scala-stm" % "0.4",
   "com.sleepycat" % "je" % "4.1.10"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked", "-Xelide-below", "INFO" ) // elide debug logging!



name                       := "LucreSTM"

version       in ThisBuild := "2.1.1"

organization  in ThisBuild := "de.sciss"

description   in ThisBuild := "Extension of Scala-STM, adding optional durability layer, and providing API for confluent and reactive event layers"

homepage      in ThisBuild := Some(url("https://github.com/Sciss/" + name.value))

scalaVersion  in ThisBuild := "2.11.2"

crossScalaVersions in ThisBuild := Seq("2.11.2", "2.10.4")

libraryDependencies in ThisBuild +=
  "org.scalatest" %% "scalatest" % "2.2.2" % "test"

// retrieveManaged in ThisBuild  := true

scalacOptions   in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")

lazy val loggingEnabled = false  // only effective for snapshot versions

scalacOptions   in ThisBuild ++= {
  if (loggingEnabled && isSnapshot.value) Nil else Seq("-Xelide-below", "INFO")     // elide debug logging!
}

testOptions     in Test       += Tests.Argument("-oDF")   // ScalaTest: durations and full stack traces

parallelExecution in Test := false

// ---- publishing ----

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild :=
  Some(if (isSnapshot.value)
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
}

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("stm", "software-transactional-memory", "persistent")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

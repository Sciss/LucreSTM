name := "LucreSTM"

version in ThisBuild := "2.0.1-SNAPSHOT"

organization in ThisBuild := "de.sciss"

description in ThisBuild := "Extension of Scala-STM, adding optional durability layer, and providing API for confluent and reactive event layers"

homepage in ThisBuild <<= name { n => Some(url("https://github.com/Sciss/" + n)) }

licenses in ThisBuild := Seq("GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt"))

scalaVersion in ThisBuild := "2.10.1"

libraryDependencies in ThisBuild ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"
)

retrieveManaged in ThisBuild := true

scalacOptions in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions in ThisBuild += "-no-specialization"  // never use this shit. will give you runtime IllegalAccessErrors in random places of the future. do _not_ use specialization. ever. don't diminish your life expectancy.

scalacOptions in ThisBuild ++= Seq("-Xelide-below", "INFO")     // elide debug logging!

testOptions in Test += Tests.Argument("-oDF")   // ScalaTest: durations and full stack traces

parallelExecution in Test := false

// ---- publishing ----

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version { v =>
  Some(if (v endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )
}

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild <<= name { n =>
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

(LsKeys.tags in LsKeys.lsync) := Seq("stm", "software-transactional-memory", "persistent")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) <<= name(Some(_))

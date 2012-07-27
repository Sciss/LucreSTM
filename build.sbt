name := "LucreSTM"

version := "0.34-SNAPSHOT"

organization := "de.sciss"

description := "Combination of Scala-STM, a persistent key-value store, and observer-pattern based reactors"

homepage := Some( url( "https://github.com/Sciss/LucreSTM" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.2"

crossScalaVersions := Seq( "2.10.0-M6", "2.9.2" )

resolvers ++= Seq(
   "Oracle Repository" at "http://download.oracle.com/maven",
   "Sonatype OSS Releases" at "https://oss.sonatype.org/content/groups/public"
)

libraryDependencies ++= Seq(
   "org.scala-tools" %% "scala-stm" % "0.6",
   "com.sleepycat" % "je" % "5.0.58"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" ) // , "-Xelide-below", "INFO" ) // elide debug logging!

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution in Test := false

// publishArtifact in (Compile, packageDoc) := false   // disable doc generation during development cycles

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/LucreSTM.git</url>
  <connection>scm:git:git@github.com:Sciss/LucreSTM.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- ls.implicit.ly ----

seq( lsSettings :_* )

(LsKeys.tags in LsKeys.lsync) := Seq( "stm", "software-transactional-memory", "reactive", "event", "persistent" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "LucreSTM" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

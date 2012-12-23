name := "LucreSTM"

version in ThisBuild := "1.6.0-SNAPSHOT"

organization in ThisBuild := "de.sciss"

description in ThisBuild := "Extension of Scala-STM, adding optional durability layer, and providing API for confluent and reactive event layers"

homepage in ThisBuild := Some( url( "https://github.com/Sciss/LucreSTM" ))

licenses in ThisBuild := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion in ThisBuild := "2.10.0"

crossScalaVersions in ThisBuild := Seq( "2.10.0", "2.9.2" )

libraryDependencies in ThisBuild += "org.scala-stm" %% "scala-stm" % "0.7"

retrieveManaged in ThisBuild := true

scalacOptions in ThisBuild ++= Seq( "-deprecation", "-unchecked" ) // , "-Xelide-below", "INFO" ) // elide debug logging!

testOptions in Test += Tests.Argument( "-oDF" )   // ScalaTest: durations and full stack traces

parallelExecution in Test := false

// publishArtifact in (Compile, packageDoc) := false   // disable doc generation during development cycles

// ---- build info ----

// buildInfoSettings
// 
// sourceGenerators in Compile <+= buildInfo
// 
// buildInfoKeys := Seq( name, organization, version, scalaVersion, description,
//    BuildInfoKey.map( homepage ) { case (k, opt) => k -> opt.get },
//    BuildInfoKey.map( licenses ) { case (_, Seq( (lic, _) )) => "license" -> lic }
// )
// 
// buildInfoPackage := "de.sciss.lucre.stm"

// ---- publishing ----

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild :=
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

(LsKeys.tags in LsKeys.lsync) := Seq( "stm", "software-transactional-memory", "persistent" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "LucreSTM" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

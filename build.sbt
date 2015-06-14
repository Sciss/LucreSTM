lazy val baseName         = "LucreSTM"
lazy val baseNameL        = baseName.toLowerCase
lazy val projectVersion   = "2.2.0-SNAPSHOT"

lazy val sleepyVersion5   = "5.0.104" // = Berkeley DB Java Edition; note: version 6 requires Java 7
lazy val sleepyVersion6   = "6.2.7"
lazy val serialVersion    = "1.1.0-SNAPSHOT"
lazy val scalaSTMVersion  = "0.7"
lazy val scalaTestVersion = "2.2.5"

lazy val commonSettings = Seq(
  version             := projectVersion,
  organization        := "de.sciss",
  description         := "Extension of Scala-STM, adding optional durability layer, and providing API for confluent and reactive event layers",
  homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
  scalaVersion        := "2.11.6",
  crossScalaVersions  := Seq("2.11.6", "2.10.5"),
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture"),
  scalacOptions      ++= {
    if (loggingEnabled && isSnapshot.value) Nil else Seq("-Xelide-below", "INFO")     // elide debug logging!
  },
  testOptions in Test += Tests.Argument("-oDF"),   // ScalaTest: durations and full stack traces
  parallelExecution in Test := false,
  libraryDependencies += "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
) ++ publishSettings

lazy val lgpl = "LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")
lazy val gpl2 = "GPL v2+"    -> url("http://www.gnu.org/licenses/gpl-2.0.txt" )
lazy val gpl3 = "GPL v3+"    -> url("http://www.gnu.org/licenses/gpl-3.0.txt" )

lazy val root: Project = Project(id = baseNameL, base = file(".")).
  aggregate(core, bdb, bdb6).
  dependsOn(core, bdb /* , bdb6 */). // i.e. root = full sub project. if you depend on root, will draw all sub modules.
  settings(commonSettings).
  settings(
    licenses := Seq(gpl2),
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false  // there are no sources
  )

lazy val core = Project(id = s"$baseNameL-core", base = file("core")).
  enablePlugins(BuildInfoPlugin).
  settings(commonSettings).
  settings(
    licenses := Seq(lgpl),
    libraryDependencies ++= Seq(
      "org.scala-stm" %% "scala-stm" % scalaSTMVersion,
      "de.sciss"      %% "serial"    % serialVersion
    ),
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) {
        case (k, opt) => k -> opt.get
      },
      BuildInfoKey.map(licenses) {
        case (_, Seq((lic, _))) => "license" -> lic
      }
    ),
    buildInfoPackage := "de.sciss.lucre.stm"
  )

lazy val bdb = Project(id = s"$baseNameL-bdb", base = file("bdb")).
  dependsOn(core).
  settings(commonSettings).
  settings(
    licenses := Seq(gpl2),
    resolvers += "Oracle Repository" at "http://download.oracle.com/maven", // required for sleepycat
    libraryDependencies += "com.sleepycat" % "je" % sleepyVersion5
  )

lazy val bdb6 = Project(id = s"$baseNameL-bdb6", base = file("bdb6")).
  dependsOn(core).
  settings(commonSettings).
  settings(
    licenses := Seq(gpl3),
    resolvers += "Oracle Repository" at "http://download.oracle.com/maven",
    libraryDependencies += "com.sleepycat" % "je" % sleepyVersion6
  )

lazy val loggingEnabled = false  // only effective for snapshot versions

// ---- publishing ----

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
<scm>
  <url>git@github.com:Sciss/{baseName}.git</url>
  <connection>scm:git:git@github.com:Sciss/{baseName}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
  }
)

// ---- ls.implicit.ly ----

// seq(lsSettings :_*)
// (LsKeys.tags   in LsKeys.lsync) := Seq("stm", "software-transactional-memory", "persistent")
// (LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")
// (LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

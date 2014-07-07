import sbt._
import Keys._
import sbtbuildinfo.Plugin._

object Build extends sbt.Build {
  def sleepyVersion   = "5.0.104" // = Berkeley DB Java Edition; note: version 6 requires Java 7
  def serialVersion   = "1.0.2"
  def scalaSTMVersion = "0.7"

  lazy val lgpl = "LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")
  lazy val gpl  = "GPL v2+"    -> url("http://www.gnu.org/licenses/gpl-2.0.txt" )

  lazy val root: Project = Project(
    id            = "lucrestm",
    base          = file("."),
    aggregate     = Seq(core, bdb),
    dependencies  = Seq(core, bdb), // i.e. root = full sub project. if you depend on root, will draw all sub modules.
    settings      = Project.defaultSettings ++ Seq(
      licenses := Seq(gpl),
      publishArtifact in (Compile, packageBin) := false, // there are no binaries
      publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
      publishArtifact in (Compile, packageSrc) := false  // there are no sources
    )
  )

  lazy val core = Project(
    id            = "lucrestm-core",
    base          = file("core"),
    settings      = Project.defaultSettings ++ buildInfoSettings ++ Seq(
      licenses := Seq(lgpl),
      libraryDependencies ++= Seq(
        "org.scala-stm" %% "scala-stm" % scalaSTMVersion,
        "de.sciss"      %% "serial"    % serialVersion
      ),
      // buildInfoSettings
      sourceGenerators in Compile <+= buildInfo,
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
  )

  lazy val bdb = Project(
    id            = "lucrestm-bdb",
    base          = file("bdb"),
    dependencies  = Seq(core),
    settings      = Project.defaultSettings ++ Seq(
      licenses := Seq(gpl),
      resolvers += "Oracle Repository" at "http://download.oracle.com/maven", // required for sleepycat
      libraryDependencies += "com.sleepycat" % "je" % sleepyVersion
    )
  )
}

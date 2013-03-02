import sbt._
import Keys._
import sbtbuildinfo.Plugin._

object Build extends sbt.Build {
  lazy val root: Project = Project(
    id            = "lucrestm",
    base          = file("."),
    aggregate     = Seq(serial, core, bdb),
    dependencies  = Seq(serial, core, bdb), // i.e. root = full sub project. if you depend on root, will draw all sub modules.
    settings      = Project.defaultSettings ++ Seq(
      publishArtifact in (Compile, packageBin) := false, // there are no binaries
      publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
      publishArtifact in (Compile, packageSrc) := false  // there are no sources
    )
  )

  lazy val serial = Project(
    id        = "lucrestm-serial",
    base      = file("serial"),
    settings  = Project.defaultSettings ++ Seq(
      licenses := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt"))
    )
  )

  lazy val core = Project(
    id            = "lucrestm-core",
    base          = file("core"),
    dependencies  = Seq(serial),
    settings      = Project.defaultSettings ++ buildInfoSettings ++ Seq(
      libraryDependencies += "org.scala-stm" %% "scala-stm" % "0.7",
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
      resolvers += "Oracle Repository" at "http://download.oracle.com/maven", // required for sleepycat
      libraryDependencies += "com.sleepycat" % "je" % "5.0.58" // = Berkeley DB Java Edition
    )
  )
}

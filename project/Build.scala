import sbt._
import Keys._
import sbtbuildinfo.Plugin._

object Build extends sbt.Build {
   lazy val audiowidgets: Project = Project(
      id        = "lucrestm",
      base      = file( "." ),
      aggregate = Seq( core, bdb )
   )

   lazy val core = Project(
      id        = "lucrestm-core",
      base      = file( "core" ),
      settings     = Project.defaultSettings ++ buildInfoSettings ++ Seq(
         // buildInfoSettings
         sourceGenerators in Compile <+= buildInfo,
         buildInfoKeys := Seq( name, organization, version, scalaVersion, description,
            BuildInfoKey.map( homepage ) { case (k, opt) => k -> opt.get },
            BuildInfoKey.map( licenses ) { case (_, Seq( (lic, _) )) => "license" -> lic }
         ),
         buildInfoPackage := "de.sciss.lucre.stm"
      )
   )

   lazy val bdb = Project(
      id           = "lucrestm-bdb",
      base         = file( "bdb" ),
      dependencies = Seq( core ),
      settings     = Project.defaultSettings ++ Seq(
         resolvers += "Oracle Repository" at "http://download.oracle.com/maven",  // required for sleepycat
         libraryDependencies += "com.sleepycat" % "je" % "5.0.58" // = Berkeley DB Java Edition
      )
   )
}

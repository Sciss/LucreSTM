package de.sciss.lucrestm

import annotation.elidable
import elidable.CONFIG
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

object LucreSTM {
   private lazy val logHeader = new SimpleDateFormat( "[d MMM yyyy, HH:mm''ss.SSS] 'Lucre' - ", Locale.US )

   val name          = "LucreSTM"
   val version       = 0.10
   val copyright     = "(C)opyright 2011 Hanns Holger Rutz"
   val isSnapshot    = false

   def versionString = {
      val s = (version + 0.001).toString.substring( 0, 4 )
      if( isSnapshot ) s + "-SNAPSHOT" else s
   }

   def main( args: Array[ String ]) {
      printInfo()
      sys.exit( 1 )
   }

   def printInfo() {
      println( "\n" + name + " v" + versionString + "\n" + copyright +
         ". All rights reserved.\n\nThis is a library which cannot be executed directly.\n" )
   }

   @elidable(CONFIG) private[lucrestm] def logConfig( what: String ) {
//      log.info( what )
      println( logHeader.format( new Date() ) + what )
   }
}
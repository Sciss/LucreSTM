/*
 *  LucreSTM.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre

import annotation.elidable
import elidable.CONFIG
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

object LucreSTM {
   private lazy val logHeader = new SimpleDateFormat( "[d MMM yyyy, HH:mm''ss.SSS] 'Lucre' - ", Locale.US )

   val name          = "LucreSTM"
   val version       = 0.21
   val copyright     = "(C)opyright 2011-2012 Hanns Holger Rutz"
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

   @elidable(CONFIG) private[lucre] def logConfig( what: String ) {
//      log.info( what )
      println( logHeader.format( new Date() ) + what )
   }
}
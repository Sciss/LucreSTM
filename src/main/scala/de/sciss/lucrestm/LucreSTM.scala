package de.sciss.lucrestm

import annotation.elidable
import elidable.CONFIG
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

//import com.weiglewilczek.slf4s.Logger

object LucreSTM {
//   lazy private val log = Logger( "LucreSTM" )
   private lazy val logHeader = new SimpleDateFormat( "[d MMM yyyy, HH:mm''ss.SSS] 'Lucre' - ", Locale.US )

   @elidable(CONFIG) private[lucrestm] def logConfig( what: String ) {
//      log.info( what )
      println( logHeader.format( new Date() ) + what )
   }
}
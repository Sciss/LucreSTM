package de.sciss.lucrestm

import annotation.elidable
import elidable.CONFIG
import com.weiglewilczek.slf4s.Logger

object LucreSTM {
   lazy private val log = Logger( "LucreSTM" )

   @elidable(CONFIG) private[lucrestm] def logConfig( what: String ) { log.info( what )}
}
package de.sciss.lucre.stm
package impl

import concurrent.stm.TxnLocal

final class LocalVarImpl[ S <: Sys[ S ], A ]( init: S#Tx => A )
extends LocalVar[ S#Tx, A ] {
   private val peer = TxnLocal[ A ]()

   override def toString = "TxnLocal<" + hashCode().toHexString + ">"

   def get( implicit tx: S#Tx ) : A = {
      implicit val itx = tx.peer
      if( peer.isInitialized ) peer.get else {
         val initVal = init( tx )
         peer.set( initVal )
         initVal
      }
   }

   def set( v: A )( implicit tx: S#Tx ) {
      peer.set( v )( tx.peer )
   }

   def isInitialized( implicit tx: S#Tx ) : Boolean = peer.isInitialized( tx.peer )
}

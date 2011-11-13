package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => STMRef}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}

object InMemory {
   final class Ref[ A ] private[InMemory] ( peer: ScalaRef[ A ]) extends STMRef[ InTxn, A ] {
      def set( v: A )( implicit tx: InTxn ) { peer.set( v )}
      def get( implicit tx: InTxn ) : A = peer.get
//      def update( v: A )( implicit tx: InTxn ) { peer.set( v )}
//      def apply()( implicit tx: InTxn ) : A = peer.get
   }
}

/**
 * A thin wrapper around scala-stm.
 */
class InMemory extends Sys[ InMemory ] {
   type Ref[ A ]  = InMemory.Ref[ A ]
   type Tx        = InTxn

   def newRef[ A ]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : InMemory.Ref[ A ] = {
      val peer = ScalaRef[ A ]( init )
      new InMemory.Ref[ A ]( peer )
   }

   def disposeRef[ A ]( ref: InMemory.Ref[ A ])( implicit tx: InTxn ) {}

   def atomic[ Z ]( block: InTxn => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( block )
   }

   def newRefArray[ A ]( size: Int ) = new Array[ InMemory.Ref[ A ]]( size )
}
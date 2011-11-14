package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => STMRef}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}
import java.io.{ObjectOutputStream, ObjectInputStream}

object InMemory {
   final class Ref[ A ] private[InMemory] ( peer: ScalaRef[ A ] /*, private[InMemory] val ref: Serializer[ A ] */)
   extends STMRef[ InTxn, A ] {
      def set( v: A )( implicit tx: InTxn ) { peer.set( v )}
      def get( implicit tx: InTxn ) : A = peer.get
//      def update( v: A )( implicit tx: InTxn ) { peer.set( v )}
//      def apply()( implicit tx: InTxn ) : A = peer.get
   }

//   private final class RefSer[ A ]( serA: Serializer[ A ]) extends Serializer[ Ref[ A ]] {
//      def read( is: ObjectInputStream ) : Ref[ A ] = {
//         val v = serA.read( is )
//         new Ref[ A ]( ScalaRef( v ))
//      }
//
//      def write( ref: Ref[ A ], os: ObjectOutputStream ) {
//         ref.get
//      }
//   }
}

/**
 * A thin wrapper around scala-stm.
 */
final class InMemory extends Sys[ InMemory ] {
   type Ref[ A ]  = InMemory.Ref[ A ]
   type Tx        = InTxn

   def newRef[ A ]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : InMemory.Ref[ A ] = {
      val peer = ScalaRef[ A ]( init )
      new InMemory.Ref[ A ]( peer )
   }

   def newRefArray[ A ]( size: Int ) = new Array[ InMemory.Ref[ A ]]( size )

   def atomic[ Z ]( block: InTxn => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( block )
   }

   def readRef[ A ]( is: ObjectInputStream )( implicit ser: Serializer[ A ]) : Ref[ A ] = {
      sys.error( "Operation not supported: readRef" )
   }

   def writeRef[ A ]( ref: Ref[ A ], os: ObjectOutputStream ) {
      sys.error( "Operation not supported: writeRef" )
   }

   def disposeRef[ A ]( ref: InMemory.Ref[ A ])( implicit tx: InTxn ) {}
}
package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}

object InMemory {
   final class Ref[ A ] private[InMemory] ( peer: ScalaRef[ A ] /*, private[InMemory] val ref: Serializer[ A ] */)
   extends _Ref[ InTxn, A ] {
      def set( v: A )( implicit tx: InTxn ) { peer.set( v )}
      def get( implicit tx: InTxn ) : A = peer.get
      def transform( f: A => A )( implicit tx: InTxn ) { peer.transform( f )}
      def dispose()( implicit tx: InTxn ) { peer.set( null.asInstanceOf[ A ])}
      def write( out: DataOutput ) {}

      def debug() {}
//      def update( v: A )( implicit tx: InTxn ) { peer.set( v )}
//      def apply()( implicit tx: InTxn ) : A = peer.get
   }

   sealed trait Mut[ A ] extends Mutable[ InTxn, A ]

   private final class MutImpl[ A <: Disposable[ InTxn ]]( value: A ) extends Mut[ A ] {
      def get( implicit tx: InTxn ) : A = value
      def write( out: DataOutput ) {
         opNotSupported( "write" )
      }
      def dispose()( implicit tx: InTxn ) { value.dispose }
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

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )
}

/**
 * A thin wrapper around scala-stm.
 */
final class InMemory extends Sys[ InMemory ] {
   type Ref[ A ]  = InMemory.Ref[ A ]
   type Mut[ A ]  = InMemory.Mut[ A ]
   type Tx        = InTxn

   def newRef[ A ]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : InMemory.Ref[ A ] = {
      val peer = ScalaRef[ A ]( init )
      new InMemory.Ref[ A ]( peer )
   }

   def newMut[ A <: Disposable[ InTxn ]]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : InMemory.Mut[ A ] =
      new InMemory.MutImpl[ A ]( init )

   def newRefArray[ A ]( size: Int ) = new Array[ InMemory.Ref[ A ]]( size )

   def atomic[ Z ]( block: InTxn => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( block )
   }

   def readRef[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Ref[ A ] = {
      InMemory.opNotSupported( "readRef" )
   }

   def readMut[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Mut[ A ] = {
      InMemory.opNotSupported( "readMut" )
   }

//   def writeRef[ A ]( ref: Ref[ A ], out: DataOutput ) {
//      sys.error( "Operation not supported: writeRef" )
//   }

//   def disposeRef[ A ]( ref: InMemory.Ref[ A ])( implicit tx: InTxn ) {}
}
package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}

object InMemory {
   sealed trait Ref[ A ] extends _Ref[ InTxn, A ]

   private sealed trait SourceImpl[ A ] {
      protected def peer: ScalaRef[ A ]
      def set( v: A )( implicit tx: InTxn ) { peer.set( v )}
      def get( implicit tx: InTxn ) : A = peer.get
      def transform( f: A => A )( implicit tx: InTxn ) { peer.transform( f )}
      def dispose()( implicit tx: InTxn ) { peer.set( null.asInstanceOf[ A ])}
      def write( out: DataOutput ) {}

      def debug() {}
//      def update( v: A )( implicit tx: InTxn ) { peer.set( v )}
//      def apply()( implicit tx: InTxn ) : A = peer.get
   }

   private final class RefImpl[ A ]( protected val peer: ScalaRef[ Mut[ A ]])
   extends Ref[ Mut[ A ]] with SourceImpl[ Mut[ A ]] {
   }

   private final class ValImpl[ A ]( protected val peer: ScalaRef[ A ])
   extends Ref[ A ] with SourceImpl[ A ] {
   }

   sealed trait Mut[ A ] extends Mutable[ InTxn, A ]

   private final class MutImpl[ A <: Disposable[ InTxn ]]( value: A ) extends Mut[ A ] {
      def isEmpty   : Boolean = false
      def isDefined : Boolean = true

      def orNull[ A1 >: A ]( implicit tx: InTxn, ev: <:<[ Null, A1 ]) : A1 = get

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
   type Val[ A ]  = InMemory.Ref[ A ]
   type Mut[ A ]  = InMemory.Mut[ A ]
   type Tx        = InTxn

   def newVal[ A ]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : Val[ A ] = {
      val peer = ScalaRef[ A ]( init )
      new InMemory.ValImpl[ A ]( peer )
   }

   def newRef[ A <: Disposable[ InTxn ]]()( implicit tx: InTxn, ser: Serializer[ A ]) : Ref[ A ] = newRef[ A ]( null )

   def newRef[ A <: Disposable[ InTxn ]]( init: Mut[ A ])( implicit tx: InTxn, ser: Serializer[ A ]) : Ref[ A ] = {
      val peer = ScalaRef[ Mut[ A ]]( init )
      new InMemory.RefImpl[ A ]( peer )
   }

   def newMut[ A <: Disposable[ InTxn ]]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : Mut[ A ] =
      new InMemory.MutImpl[ A ]( init )

   def newValArray[ A ]( size: Int ) = new Array[ Val[ A ]]( size )

   def newRefArray[ A ]( size: Int ) = new Array[ Ref[ A ]]( size )

   def atomic[ Z ]( block: InTxn => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( block )
   }

   def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Val[ A ] = {
      InMemory.opNotSupported( "readVal" )
   }

   def readRef[ A <: Disposable[ InTxn ]]( in: DataInput )( implicit ser: Serializer[ A ]) : Ref[ A ] = {
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
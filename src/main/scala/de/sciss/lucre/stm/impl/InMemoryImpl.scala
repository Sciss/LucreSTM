package de.sciss.lucre
package stm
package impl

import concurrent.stm.{Ref => ScalaRef, TxnExecutor, InTxn}
import stm.{Txn => _Txn, Var => _Var}

object InMemoryImpl {
   private type S = InMemory

//   import InMemory.{ID, Var, Txn}

   def apply() : InMemory = new System

//   def ??? : Nothing = sys.error( "TODO" )

   trait Mixin[ S <: InMemoryLike[ S ]] extends InMemoryLike[ S ] {
//      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, S ]( new VarImpl( ScalaRef( 0 )))
      private val idCnt = ScalaRef( 0 )

      final def newID( peer: InTxn ) : S#ID = {
//         // since idCnt is a ScalaRef and not InMemory#Var, make sure we don't forget to mark the txn dirty!
//         dirty = true
         val res = idCnt.get( peer ) + 1
         idCnt.set( res )( peer )
         new IDImpl[ S ]( res )
      }

//      def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ] = v

      final def root[ A ]( init: S#Tx => A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ] = {
         step { implicit tx =>
            tx.newVar[ A ]( tx.newID(), init( tx ))
         }
      }

      final def close() {}

      // ---- cursor ----

      final def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic( itx => fun( wrap( itx ))) // new TxnImpl( this, itx )))
      }

      final def position( implicit tx: S#Tx ) : S#Acc = ()

//      def position_=( path: S#Acc )( implicit tx: S#Tx ) {}
//
//      def wrap( itx: InTxn ) : S#Tx // = new TxnImpl( this, itx )
   }

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   private final class VarImpl[ S <: InMemoryLike[ S ], @specialized A ]( peer: ScalaRef[ A ])
   extends _Var[ S#Tx, A ] /* with VarLike[ A, InTxn ] */ {
      override def toString = "Var<" + hashCode().toHexString + ">"

      def get( implicit tx: S#Tx ) : A = peer.get( tx.peer )

      def set( v: A )( implicit tx: S#Tx ) {
         peer.set( v )( tx.peer )
//         tx.markDirty()
      }

      def transform( f: A => A )( implicit tx: S#Tx ) {
         peer.transform( f )( tx.peer )
//         tx.markDirty()
      }

      def write( out: DataOutput ) {}

      def dispose()( implicit tx: S#Tx ) {
         peer.set( null.asInstanceOf[ A ])( tx.peer )
//         tx.markDirty()
      }

//      def isFresh( implicit tx: S#Tx ) : Boolean = true
   }

   private final class IDImpl[ S <: InMemoryLike[ S ]]( val id: Int ) extends InMemoryLike.ID[ S ] {
      def write( out: DataOutput ) {}

      def dispose()( implicit tx: S#Tx ) {}

      override def toString = "<" + id + ">"

      override def hashCode : Int = id.##
      override def equals( that: Any ) = that.isInstanceOf[ InMemoryLike.ID[ _ ]] &&
         (that.asInstanceOf[ InMemoryLike.ID[ _ ]].id == id)
   }

   private final class TxnImpl( val system: InMemory, val peer: InTxn )
   extends TxnMixin[ InMemory ] // with InMemory.Txn

   trait TxnMixin[ S <: InMemoryLike[ S ]] extends _Txn[ S ] {
//      this: S#Tx =>

//      private var dirty = false
//
//      def isDirty = dirty
//
//      def markDirty() { dirty = true }

//      def inMemory: InMemory#Tx = this

      def newID() : S#ID = system.newID( peer )
      def newPartialID(): S#ID = newID()

//      def reactionMap: ReactionMap[ S ] = system.reactionMap

      def newVar[ A ]( id: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newLocalVar[ A ]( init: S#Tx => A ) : LocalVar[ S#Tx, A ] = new impl.LocalVarImpl[ S, A ]( init )

      def newPartialVar[ A ]( id: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]): S#Var[ A ] =
         newVar( id, init )

      def newIntVar( id: S#ID, init: Int ) : S#Var[ Int ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newBooleanVar( id: S#ID, init: Boolean ) : S#Var[ Boolean ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newLongVar( id: S#ID, init: Long ) : S#Var[ Long ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newVarArray[ A ]( size: Int ) = new Array[ S#Var[ A ] ]( size )

      def newInMemoryIDMap[ A ] : IdentifierMap[ S#ID, S#Tx, A ] =
         IdentifierMap.newInMemoryIntMap[ S#ID, S#Tx, A ]( new IDImpl( 0 ))( _.id )

      def newDurableIDMap[ A ]( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] =
         IdentifierMap.newInMemoryIntMap[ S#ID, S#Tx, A ]( new IDImpl( 0 ))( _.id )

//      def readVal[ A ]( id: S#ID )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : A = opNotSupported( "readVal" )
//
//      def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) {}

      def readVar[ A ]( id: S#ID, in: DataInput )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         opNotSupported( "readVar" )
      }

      def readPartialVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] =
         readVar( pid, in )

      def readBooleanVar( id: S#ID, in: DataInput ) : S#Var[ Boolean ] = {
         opNotSupported( "readBooleanVar" )
      }

      def readIntVar( id: S#ID, in: DataInput ) : S#Var[ Int ] = {
         opNotSupported( "readIntVar" )
      }

      def readLongVar( id: S#ID, in: DataInput ) : S#Var[ Long ] = {
         opNotSupported( "readLongVar" )
      }

      def readID( in: DataInput, acc: S#Acc ) : S#ID = opNotSupported( "readID" )
      def readPartialID( in: DataInput, acc: S#Acc ) : S#ID = readID( in, acc )

      def readDurableIDMap[ A ]( in: DataInput )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] =
         opNotSupported( "readDurableIDMap" )

      def refresh[ A ]( access: S#Acc, value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : A = value
   }

   private final class System extends Mixin[ InMemory ] with InMemory {
      private type S = InMemory

      override def toString = "InMemory@" + hashCode.toHexString

      def wrap( itx: InTxn ) : S#Tx = new TxnImpl( this, itx )
   }
}
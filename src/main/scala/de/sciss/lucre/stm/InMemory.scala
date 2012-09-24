/*
 *  InMemory.scala
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
package stm

import stm.{Var => _Var, Txn => _Txn}
import event.ReactionMap
import concurrent.stm.{Ref => ScalaRef, TxnExecutor, InTxn}

object InMemory {
   private type S = InMemory

   sealed trait Var[ @specialized A ] extends _Var[ S#Tx, A ]

   sealed trait Txn extends _Txn[ S ] {
//      private[InMemory] def markDirty() : Unit
   }

//   private sealed trait VarLike[ @specialized A, Context >: InTxn <: InTxnEnd ] {
//      protected def peer: RefLike[ A, Context ]
//
//   }

   private final class VarImpl[ @specialized A ]( peer: ScalaRef[ A ])
   extends Var[ A ] /* with VarLike[ A, InTxn ] */ {
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

      def isFresh( implicit tx: S#Tx ) : Boolean = true
   }

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   sealed trait ID extends Identifier[ S#Tx ] {
      private[InMemory] def id: Int
   }

   private final class IDImpl( private[InMemory] val id: Int ) extends ID {
      def write( out: DataOutput ) {}

      def dispose()( implicit tx: S#Tx ) {}

      override def toString = "<" + id + ">"

      override def hashCode : Int = id.##
      override def equals( that: Any ) = that.isInstanceOf[ ID ] &&
         (that.asInstanceOf[ ID ].id == id)
   }

   private final class TxnImpl( val system: System, val peer: InTxn ) extends Txn {
//      private var dirty = false
//
//      def isDirty = dirty
//
//      def markDirty() { dirty = true }

      def inMemory: InMemory#Tx = this

      def newID() : S#ID = {
//         // since idCnt is a ScalaRef and not InMemory#Var, make sure we don't forget to mark the txn dirty!
//         dirty = true
         new IDImpl( system.newIDValue( peer ))
      }
      def newPartialID(): S#ID = newID()

      def reactionMap: ReactionMap[ S ] = system.reactionMap

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

//   private object IDOrdering extends Ordering[ S#ID ] {
//      def compare( a: S#ID, b: S#ID ) : Int = {
//         val aid = a.id
//         val bid = b.id
//         if( aid < bid ) -1 else if( aid > bid ) 1 else 0
//      }
//   }

   private final class System extends InMemory {
//      def manifest : Manifest[ S ] = Manifest.classType(classOf[ InMemory ])
//      def idOrdering : Ordering[ S#ID ] = IDOrdering

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, S ]( new VarImpl( ScalaRef( 0 )))
      private val idCnt = ScalaRef( 0 )

      private[InMemory] def newIDValue( implicit tx: InTxn ) : Int = {
         val res = idCnt.get + 1
         idCnt.set( res )
         res
      }

//      def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ] = v

      def root[ A ]( init: S#Tx => A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ] = {
         step { implicit tx =>
            tx.newVar[ A ]( tx.newID(), init( tx ))
         }
      }

      def close() {}

      // ---- cursor ----

      def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx )))
      }

      def position( implicit tx: S#Tx ) : S#Acc = ()

//      def position_=( path: S#Acc )( implicit tx: S#Tx ) {}

      def wrap( itx: InTxn ) : Tx = new TxnImpl( this, itx )
   }

   def apply() : S = new System
}

/**
 * A thin wrapper around scala-stm.
 */
sealed trait InMemory extends Sys[ InMemory ] with Cursor[ InMemory ] {
   final type Var[ @specialized A ] = InMemory.Var[ A ]
   final type ID = InMemory.ID
   final type Tx = InMemory.Txn // Txn[ InMemory ]
   final type Acc = Unit
   final type Entry[ A ] = InMemory.Var[ A ]

   def wrap( peer: InTxn ): Tx
}
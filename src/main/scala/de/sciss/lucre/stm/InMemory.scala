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
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}
import event.ReactionMap

object InMemory {
   private type S = InMemory

   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ]

   private sealed trait SourceImpl[ @specialized A ] {
      protected def peer: ScalaRef[ A ]

      def get( implicit tx: Txn ) : A = peer.get( tx.peer )

      def write( out: DataOutput ) {}
   }

   private final class VarImpl[ @specialized A ](protected val peer: ScalaRef[ A ])
      extends Var[ A ] with SourceImpl[ A ] {
      override def toString = "Var<" + hashCode().toHexString + ">"

      def set(v: A)( implicit tx: Txn ) {
         peer.set( v )( tx.peer )
      }

      def transform( f: A => A )( implicit tx: Txn ) {
         peer.transform( f )( tx.peer )
      }

      def dispose()( implicit tx: Txn ) {
         peer.set( null.asInstanceOf[ A ])( tx.peer )
      }
   }

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   sealed trait ID extends Identifier[ Txn ]

   private final class IDImpl extends ID {
      def write( out: DataOutput ) {}

      def dispose()( implicit tx: Txn ) {}

      override def toString = "<" + hashCode().toHexString + ">"
   }

   sealed trait Txn extends _Txn[ S ]

   private final class TxnImpl( val system: System, val peer: InTxn ) extends Txn {
      def newID() : ID = new IDImpl

      def reactionMap: ReactionMap[ S ] = system.reactionMap

      def newVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newIntVar( id: ID, init: Int ) : Var[ Int ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newBooleanVar( id: ID, init: Boolean ) : Var[ Boolean ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newLongVar( id: ID, init: Long ) : Var[ Long ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newVarArray[ A ]( size: Int ) = new Array[ Var[ A ] ]( size )

      def _readUgly[ A ]( parent: S#ID, id: S#ID )( implicit ser: TxnReader[ Txn, Unit, A ]) : A =
         opNotSupported( "_readUgly" )

      def _writeUgly[ A ]( parent: S#ID, id: S#ID, value: A )( implicit writer: TxnWriter[ A ]) {}

      def readVal[ A ]( id: S#ID )( implicit reader: TxnReader[ Txn, Unit, A ]) : A = opNotSupported( "readVal" )

      def writeVal( id: S#ID, value: Writer ) {}

      def readVar[ A ]( id: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         opNotSupported( "readVar" )
      }

      def readBooleanVar( id: ID, in: DataInput ) : Var[ Boolean ] = {
         opNotSupported( "readBooleanVar" )
      }

      def readIntVar( id: ID, in: DataInput ) : Var[ Int ] = {
         opNotSupported( "readIntVar" )
      }

      def readLongVar( id: ID, in: DataInput ) : Var[ Long ] = {
         opNotSupported( "readLongVar" )
      }

      def readID( in: DataInput, acc: Unit ) : ID = opNotSupported( "readID" )

      def access[ A ]( source: S#Var[ A ]) : A = source.get( this )
   }

   private final class System extends InMemory {
      def manifest: Manifest[ S ] = Manifest.classType(classOf[ InMemory ])

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, S ]( new VarImpl( ScalaRef( 0 )))

      def atomic[ A ]( fun: S#Tx => A ) : A = {
         TxnExecutor.defaultAtomic[ A ]( itx => fun( new TxnImpl( this, itx )))
      }

      def wrap( itx: InTxn ) : Tx = new TxnImpl( this, itx )
   }

   def apply() : S = new System
}

/**
 * A thin wrapper around scala-stm.
 */
sealed trait InMemory extends Sys[ InMemory ] {
   type Var[ @specialized A ] = InMemory.Var[ A ]
   type ID = InMemory.ID
   type Tx = InMemory.Txn
   type Acc = Unit

   def wrap( peer: InTxn ): Tx
}
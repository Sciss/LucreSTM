/*
 *  InMemory.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.lucrestm

import de.sciss.lucrestm.{Var => _Var, Txn => _Txn}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}

object InMemory {
   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ]

   private sealed trait SourceImpl[ @specialized A ] {
      protected def peer: ScalaRef[ A ]
      def set( v: A )( implicit tx: Txn ) { peer.set( v )( tx.peer )}
      def get( implicit tx: Txn ) : A = peer.get( tx.peer )
      def transform( f: A => A )( implicit tx: Txn ) { peer.transform( f )( tx.peer )}
      def dispose()( implicit tx: Txn ) { peer.set( null.asInstanceOf[ A ])( tx.peer )}
      def write( out: DataOutput ) {}
   }

   private final class VarImpl[ @specialized A ]( protected val peer: ScalaRef[ A ])
   extends Var[ A ] with SourceImpl[ A ] {
      override def toString = "Var<" + hashCode().toHexString + ">"
   }

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   sealed trait ID extends Identifier[ Txn ]

   private final class IDImpl extends ID {
      def write( out: DataOutput ) {}
      def dispose()( implicit tx: Txn ) {}
      override def toString = "<" + hashCode().toHexString + ">"
   }

   sealed trait Txn extends _Txn[ InMemory ]

   private final class TxnImpl( val system: InMemory, val peer: InTxn ) extends Txn {
      def newID() : ID = new IDImpl

      def newVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         val peer = ScalaRef[ A ]( init )
         new VarImpl[ A ]( peer )
      }

      def newIntVar( id: ID, init: Int ) : Var[ Int ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newVarArray[ A ]( size: Int ) = new Array[ Var[ A ]]( size )

      def readVar[ A ]( id: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         opNotSupported( "readVar" )
      }

      def readIntVar( id: ID, in: DataInput ) : Var[ Int ] = {
         opNotSupported( "readIntVar" )
      }

      def readID( in: DataInput, acc: Unit ) : ID = opNotSupported( "readID" )

//      def readMut[ A <: Mutable[ InMemory ]]( id: ID, in: DataInput )
//                                            ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
//         opNotSupported( "readMut" )
//      }
//
//      def readOptionMut[ A <: MutableOption[ InMemory ]]( id: ID, in: DataInput )
//                                                        ( implicit reader: MutableOptionReader[ ID, Txn, A ]) : A = {
//         opNotSupported( "readOptionMut" )
//      }
   }
}

/**
 * A thin wrapper around scala-stm.
 */
final class InMemory extends Sys[ InMemory ] {
   import InMemory._

   type Var[ @specialized A ] = InMemory.Var[ A ]
   type ID                    = InMemory.ID
   type Tx                    = InMemory.Txn
   type Acc                   = Unit

   def manifest: Manifest[ InMemory ] = Manifest.classType( classOf[ InMemory ])

   def atomic[ Z ]( block: Tx => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( itx => block( new TxnImpl( this, itx )))
   }
}
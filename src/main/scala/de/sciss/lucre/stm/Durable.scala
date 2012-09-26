/*
 *  Durable.scala
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
import concurrent.stm.InTxn
import impl.{DurableImpl => Impl}

object Durable {
   private type S = Durable

   def apply( store: DataStore ) : S = Impl( store )

   def apply( factory: DataStoreFactory[ DataStore ], name: String = "data" ) : S = Impl( factory, name )

//   sealed trait Var[ @specialized A ] extends _Var[ S#Tx, A ]

   // a rare moment of love for Scala today ... this view is automatically found. at least something...
   implicit def inMemory( tx: Durable#Tx ) : InMemory#Tx = tx.inMemory
}

object DurableLike {
   trait ID[ S <: DurableLike[ S ]] extends Identifier[ S#Tx ] {
      private[stm] def id: Int
   }

   trait Txn[ S <: DurableLike[ S ]] extends _Txn[ S ] {
      def newCachedVar[ A ](  init: A   )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
      def newCachedIntVar(    init: Int  ) : S#Var[ Int ]
      def newCachedLongVar(   init: Long ) : S#Var[ Long ]
      def readCachedVar[ A ]( in: DataInput )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
      def readCachedIntVar(   in: DataInput ) : S#Var[ Int ]
      def readCachedLongVar(  in: DataInput ) : S#Var[ Long ]

//      private[Durable] def markDirty() : Unit

      private[stm] def inMemory : InMemory#Tx
   }
}
trait DurableLike[ S <: DurableLike[ S ]] extends Sys[ S ] with Cursor[ S ] {
   final type Var[ @specialized A ] = _Var[ S#Tx, A ] // Durable.Var[ A ]
   final type ID                    = DurableLike.ID[ S ]
   final type Acc                   = Unit
   final type Entry[ A ]            = _Var[ S#Tx, A ] // Durable.Var[ A ]
   type Tx                         <: DurableLike.Txn[ S ]

   /**
    * Reports the current number of records stored in the database.
    */
   def numRecords( implicit tx: S#Tx ) : Int

   /**
    * Reports the current number of user records stored in the database.
    * That is the number of records minus those records used for
    * database maintenance.
    */
   def numUserRecords( implicit tx: S#Tx ) : Int

   def debugListUserRecords()( implicit tx: S#Tx ) : Seq[ ID ]

   private[stm] def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: S#Tx ): A

   private[stm] def tryRead[ A ]( id: Long )( valueFun: DataInput => A )( implicit tx: S#Tx ): Option[ A ]

   private[stm] def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: S#Tx ): Unit

   private[stm] def write( id: Long )( valueFun: DataOutput => Unit )( implicit tx: S#Tx ): Unit

   private[stm] def remove( id: Int )( implicit tx: S#Tx ) : Unit

   private[stm] def remove( id: Long )( implicit tx: S#Tx ) : Unit

   private[stm] def exists( id: Int )( implicit tx: S#Tx ) : Boolean

   private[stm] def exists( id: Long )( implicit tx: S#Tx ) : Boolean

   private[stm] def newIDValue()( implicit tx: S#Tx ) : Int

   private[lucre] def wrap( peer: InTxn ) : S#Tx  // XXX TODO this might go in Cursor?

   def inMemory : InMemory
}

trait Durable extends DurableLike[ Durable ] {
   final type Tx                    = DurableLike.Txn[ Durable ] // Txn[ Durable ]

//   final type IM                    = InMemory

//   private[stm] def store : DataStore

//   final def inMemory[ A ]( fun: IM#Tx => A )( implicit tx: Tx ) : A = fun( tx.inMemory )
//   // final protected def fix[ A ]( v: Durable#IM#Var[ A ]) : IM#Var[ A ] = v

//   private type S = Durable
//
//   final def im( tx: S#Tx ) : IM#Tx = tx.inMemory
//   final def imVar[ A ]( v: InMemory#Var[ A ]) : InMemory#Var[ A ] = v
//   final def fixIM( id: S#IM#ID ) : IM#ID = id
//   final def fixIM[ A ]( v: S#IM#Var[ A ]) : IM#Var[ A ] = v

//   final def fixIM[ A[ _ <: S#IM ]]( a: A[ S#IM ]) : A[ IM ] = a
}
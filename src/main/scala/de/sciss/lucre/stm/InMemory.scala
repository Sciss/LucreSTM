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
import concurrent.stm.InTxn
import impl.{InMemoryImpl => Impl}

object InMemory {
   private type S = InMemory

//   trait Txn extends _Txn[ S ]
//   trait Var[ @specialized A ] extends _Var[ S#Tx, A ]
//   trait ID extends Identifier[ S#Tx ] {
//      private[stm] def id: Int
//   }

//   type ID = InMemoryLike.ID[ InMemory ]

   def apply() : S = Impl()
}

object InMemoryLike {
   trait ID[ S <: InMemoryLike[ S ]] extends Identifier[ S#Tx ] {
      private[stm] def id: Int
   }

//   trait Var[ S <: InMemoryLike[ S ], Tx <: Txn[ S ], A ] extends _Var[ Tx, A ]
}
trait InMemoryLike[ S <: InMemoryLike[ S ]] extends Sys[ S ] with Cursor[ S ] {
   _:S =>
//   final type Var[ @specialized A ] = InMemory.Var[ A ]

   type Var[ @specialized A ] = _Var[ S#Tx, A ]
   type Entry[ A ]            = _Var[ S#Tx, A ] // InMemory.Var[ A ]
   final type ID              = InMemoryLike.ID[ S ]
   final type Acc             = Unit

   private[stm]   def newID( peer: InTxn ) : S#ID
   private[lucre] def wrap(  peer: InTxn ) : S#Tx
}

/**
 * A thin in-memory (non-durable) wrapper around Scala-STM.
 */
trait InMemory extends InMemoryLike[ InMemory ] {
//   final type ID = InMemory.ID
   final type Tx  = _Txn[ InMemory ] // InMemory.Txn
   final type IM  = InMemory

//   override final type Var[ @specialized A ] = _Var[ /* S#*/ Tx, A ]
//   override final type Entry[ A ]            = _Var[ /* S# */ Tx, A ] // InMemory.Var[ A ]

//   // 'pop' the representation type ?!
//   protected def fix[ A ](v: _Var[ InMemory#IM#Tx, A ]) : _Var[ IM#Tx, A ] = v
   //   def peer( tx: S#Tx ) : IM#Tx
//   final def inMemory[ A ]( fun: InMemory#IM#Tx => A )( implicit tx: InMemory#Tx ) : A = fun( tx )

   private type S = InMemory

//   final def fixIM[ A ]( v: InMemory#IM#Var[ A ]) : IM#Var[ A ] = v
//   final def fixIM( id: InMemory#IM#ID ) : IM#ID = id

   final def im( tx: S#Tx ) : IM#Tx = tx.inMemory
//   final def fixIM( id: IM#ID ) : IM#ID = id
   final def imVar[ A ]( v: Var[ A ]) : Var[ A ] = v
//
//   final def fixIM[ A[ _ <: S#IM ]]( a: A[ S#IM ]) : A[ IM ] = a
}
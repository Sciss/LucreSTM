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
}
trait InMemoryLike[ S <: InMemoryLike[ S ]] extends Sys[ S ] with Cursor[ S ] {
//   final type Var[ @specialized A ] = InMemory.Var[ A ]
   final type Var[ @specialized A ] = _Var[ S#Tx, A ]
   final type ID                    = InMemoryLike.ID[ S ]
   final type Acc                   = Unit
   final type Entry[ A ]            = _Var[ S#Tx, A ] // InMemory.Var[ A ]

   private[stm] def newID( peer: InTxn ) : S#ID
   private[stm] def wrap( peer: InTxn ) : S#Tx
}

/**
 * A thin in-memory (non-durable) wrapper around Scala-STM.
 */
trait InMemory extends InMemoryLike[ InMemory ] {
//   final type ID = InMemory.ID
   final type Tx = _Txn[ InMemory ] // InMemory.Txn
}
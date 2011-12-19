/*
 *  Txn.scala
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

import concurrent.stm.InTxn

trait Txn[ S <: Sys[ S ]] {
   def system: S
   def peer: InTxn

   def newID() : S#ID

   def newVar[ A ]( id: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]

   def newIntVar( id: S#ID, init: Int ) : S#Var[ Int ]

//   def newObservableVar[ A ]( id: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#ObsVar[ A ]
//
//   def newObservableIntVar( id: S#ID, init: Int ) : S#ObsVar[ Int ]

   def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]]

   def readVar[ A ]( id: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]

   def readIntVar( id: S#ID, in: DataInput ) : S#Var[ Int ]

//   def readObservableVar[ A ]( id: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#ObsVar[ A ]
//
//   def readObservableIntVar( id: S#ID, in: DataInput ) : S#ObsVar[ Int ]

   def readID( in: DataInput, acc: S#Acc ) : S#ID

//   def readMut[ A <: Mutable[ S ]]( id: S#ID, in: DataInput )( implicit reader: MutableReader[ S#ID, S#Tx, A ]) : A
//
//   def readOptionMut[ A <: MutableOption[ S ]]( id: S#ID, in: DataInput )
//                                              ( implicit reader: MutableOptionReader[ S#ID, S#Tx, A ]) : A
}
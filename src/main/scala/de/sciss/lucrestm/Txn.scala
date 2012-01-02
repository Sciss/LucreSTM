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
import collection.immutable.{IndexedSeq => IIdxSeq}

trait Txn[ S <: Sys[ S ]] {
   def system: S
   def peer: InTxn

   def newID() : S#ID
   // note that `Repr` is only required to be subtype of `State`, but `State.addReactor` will make sure
   // that only really `StateNode` is storing observers as children. This makes it possible to
   // create a `StateObserver` for any `State` without needing to check whether the state is actually
   // a reactor source or not. This is a bit ugly, but should be working fine.
   private[lucrestm] def addStateReaction[ A, Repr <: State[ S, A, Repr ]]( reader: StateReader[ S, Repr ],
                                                          fun: (S#Tx, A) => Unit ) : StateReactor.Key[ S ]
   private[lucrestm] def mapStateTargets( in: DataInput, targets: StateTargets[ S ], keys: IIdxSeq[ Int ]) : StateReactor[ S ]
   private[lucrestm] def propagateState( key: Int, state: State[ S, _, _ ], reactions: State.Reactions ) : State.Reactions
   private[lucrestm] def removeStateReaction( key: StateReactor.Key[ S ]) : Unit

//   def addStateReaction[ A ]( reader: A, fun: (S#Tx, A) => Unit ) : StateReactorLeaf[ S ]

//   def addEventReaction( fun: S#Tx => Unit ) : EventReactorLeaf[ S ]
//   private[lucrestm] def removeStateReaction( leaf: StateReactorLeaf[ S ]) : Unit
//   private[lucrestm] def invokeStateReaction( leaf: StateReactorLeaf[ S ]) : Unit
//   private[lucrestm] def removeEventReaction( leaf: EventReactorLeaf[ S ]) : Unit
//   private[lucrestm] def invokeEventReaction( leaf: EventReactorLeaf[ S ], key: Int ) : Unit

   def newVar[ A ]( id: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
   def newIntVar( id: S#ID, init: Int ) : S#Var[ Int ]
   def newLongVar( id: S#ID, init: Long ) : S#Var[ Long ]

   def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]]

   def readVar[ A ]( id: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
   def readIntVar( id: S#ID, in: DataInput ) : S#Var[ Int ]
   def readLongVar( id: S#ID, in: DataInput ) : S#Var[ Long ]

   def readID( in: DataInput, acc: S#Acc ) : S#ID
}
/*
 *  Txn.scala
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

import concurrent.stm.InTxn
import event.ReactionMap

trait Txn[ S <: Sys[ S ]] {
   /**
    * Back link to the underlying system
    */
   def system: S

   /**
    * Every transaction has a plain Scala-STM transaction as a peer. This comes handy for
    * seting up custom things like `TxnLocal`, `TMap`, or calling into the hooks of `concurrent.stm.Txn`.
    * It is also needed when re-wrapping the transaction of one system into another.
    */
   def peer: InTxn

   def newID() : S#ID

   /**
    * The event system hook. Eventually this should be separated into a `Txn` sub type.
    */
   def reactionMap : ReactionMap[ S ]

   def newVar[ A ]( id: S#ID, init: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
   def newBooleanVar( id: S#ID, init: Boolean ) : S#Var[ Boolean ]
   def newIntVar( id: S#ID, init: Int ) : S#Var[ Int ]
   def newLongVar( id: S#ID, init: Long ) : S#Var[ Long ]

   def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ]]

   def newPartialVar[ A ]( id: S#ID, init: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]

   /**
    * Creates a new durable transactional map for storing and retrieving values based on a mutable's identifier
    * as key. If a system is confluently persistent, the `get` operation will find the most recent key that
    * matches the search key.
    *
    * ID maps can be used by observing views to look up associated view meta data even though they may be
    * presented with a more recent access path of the model peer (e.g. when a recent event is fired and observed).
    *
    * @param serializer the serializer for values in the map
    * @tparam A         the value type in the map
    */
   def newDurableIDMap[ A ]( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#Tx, S#ID, A ]

   /**
    * Creates a new in-memory transactional map for storing and retrieving values based on a mutable's identifier
    * as key. If a system is confluently persistent, the `get` operation will find the most recent key that
    * matches the search key. Objects are not serialized but kept live in memory.
    *
    * ID maps can be used by observing views to look up associated view meta data even though they may be
    * presented with a more recent access path of the model peer (e.g. when a recent event is fired and observed).
    *
    * @tparam A         the value type in the map
    */
   def newInMemoryIDMap[ A ] : IdentifierMap[ S#Tx, S#ID, A ]

   def readVar[ A ]( id: S#ID, in: DataInput )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
   def readBooleanVar( id: S#ID, in: DataInput ) : S#Var[ Boolean ]
   def readIntVar( id: S#ID, in: DataInput ) : S#Var[ Int ]
   def readLongVar( id: S#ID, in: DataInput ) : S#Var[ Long ]

   def readPartialVar[ A ]( id: S#ID, in: DataInput )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]

   /**
    * A raw read. If the underlying system doesn't persist objects, an implementation may
    * throw an exception.
    *
    * XXX TODO this is used by Compound
    */
   def _readUgly[ A ]( parent: S#ID, id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A
   /**
    * A raw write. If the underlying system doesn't persist objects, the implementation
    * should provide a no-op stub, but must not throw an exception.
    *
    * XXX TODO this is used by Compound
    */
   def _writeUgly[ A ]( parent: S#ID, id: S#ID, value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : Unit

   def readID( in: DataInput, acc: S#Acc ) : S#ID

   /**
    * XXX TODO: this is called from Targets.readAndExpand
    */
   def readVal[ A ]( id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A

   /**
    * XXX TODO: this is called from NodeSelector.writeValue which is turn is called from Targets
    */
   def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : Unit

   // XXX TODO: this merely used by ReactionTest2
   // it should be replaced by a general mechanism to turn any S#Var into a read access
   def access[ A ]( source: S#Var[ A ]) : A
}
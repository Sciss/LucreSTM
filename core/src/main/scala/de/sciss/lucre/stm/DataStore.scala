/*
 *  PersistentStore.scala
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

import concurrent.stm.Txn.ExternalDecider

trait DataStore extends ExternalDecider {
   def put(      keyFun: DataOutput => Unit )( valueFun: DataOutput => Unit )( implicit tx: Txn[ _ ]) : Unit
   def get[ A ]( keyFun: DataOutput => Unit )( valueFun: DataInput => A )(     implicit tx: Txn[ _ ]) : Option[ A ]
   def contains( keyFun: DataOutput => Unit )( implicit tx: Txn[ _ ]) : Boolean
   def remove(   keyFun: DataOutput => Unit )( implicit tx: Txn[ _ ]) : Boolean

   def flatGet[ A ]( keyFun: DataOutput => Unit )( valueFun: DataInput => Option[ A ])( implicit tx: Txn[ _ ]) : Option[ A ]

   def numEntries( implicit tx: Txn[ _ ]) : Int
   def close() : Unit
}

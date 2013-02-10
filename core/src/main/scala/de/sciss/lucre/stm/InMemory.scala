/*
 *  InMemory.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2013 Hanns Holger Rutz. All rights reserved.
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

import stm.{Var => _Var, Txn => _Txn, SpecGroup => ialized}
import concurrent.stm.InTxn
import impl.{InMemoryImpl => Impl}
import scala.{specialized => spec}

object InMemoryLike {
  trait ID[S <: InMemoryLike[S]] extends Identifier[S#Tx] {
    private[stm] def id: Int
  }
}
trait InMemoryLike[S <: InMemoryLike[S]] extends Sys[S] with Cursor[S] {
  final type Var[@spec(ialized) A]  = _Var[S#Tx, A]
  final type Entry[A]               = _Var[S#Tx, A]
  final type ID                     = InMemoryLike.ID[S]
  final type Acc                    = Unit

  private[stm] def newID(peer: InTxn): S#ID
  def wrap(peer: InTxn) : S#Tx
}

object InMemory {
  def apply(): InMemory = Impl()
}
/**
 * A thin in-memory (non-durable) wrapper around Scala-STM.
 */
trait InMemory extends InMemoryLike[InMemory] {
  final type Tx = _Txn[InMemory]
}
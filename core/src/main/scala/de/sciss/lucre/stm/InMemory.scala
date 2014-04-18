/*
 *  InMemory.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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

object InMemoryLike {
  trait ID[S <: InMemoryLike[S]] extends Identifier[S#Tx] {
    private[stm] def id: Int
  }
}
trait InMemoryLike[S <: InMemoryLike[S]] extends Sys[S] with Cursor[S] {
  final type Var[A]   = _Var[S#Tx, A]
  final type Entry[A] = _Var[S#Tx, A]
  final type ID       = InMemoryLike.ID[S]
  final type Acc      = Unit

  private[stm] def newID(peer: InTxn): S#ID
  def wrap(peer: InTxn) : S#Tx
}

object InMemory {
  def apply(): InMemory = Impl()
}
/** A thin in-memory (non-durable) wrapper around Scala-STM. */
trait InMemory extends InMemoryLike[InMemory] {
  final type Tx = _Txn[InMemory]
}
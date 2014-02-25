/*
 *  Durable.scala
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

package de.sciss
package lucre
package stm

import stm.{Var => _Var, Txn => _Txn, SpecGroup => ialized}
import serial.{DataInput, DataOutput, Serializer}
import concurrent.stm.InTxn
import impl.{DurableImpl => Impl}
import language.implicitConversions
import scala.{specialized => spec}

object Durable {
  private type S = Durable

  def apply(store: DataStore): S = Impl(store)

  def apply(factory: DataStoreFactory[DataStore], name: String = "data"): S = Impl(factory, name)

  implicit def inMemory(tx: Durable#Tx): InMemory#Tx = tx.inMemory

  trait Txn extends DurableLike.Txn[Durable] {
    private[Durable] def inMemory: InMemory#Tx
  }
}

object DurableLike {
  trait ID[S <: DurableLike[S]] extends Identifier[S#Tx] {
    private[stm] def id: Int
  }

  trait Txn[S <: DurableLike[S]] extends _Txn[S] {
    def newCachedVar[A](  init: A    )(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]
    def newCachedIntVar(  init: Int  ): S#Var[Int ]
    def newCachedLongVar( init: Long ): S#Var[Long]
    def readCachedVar[A]( in: DataInput)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]
    def readCachedIntVar( in: DataInput): S#Var[Int ]
    def readCachedLongVar(in: DataInput): S#Var[Long]
  }
}
trait DurableLike[S <: DurableLike[S]] extends Sys[S] with Cursor[S] {
  final type Var[@spec(ialized) A]  = _Var[S#Tx, A]
  final type ID                     = DurableLike.ID[S]
  final type Acc                    = Unit
  final type Entry[A]               = _Var[S#Tx, A]
  type Tx                          <: DurableLike.Txn[S]
  type I                           <: InMemoryLike[I]

  /**
   * Reports the current number of records stored in the database.
   */
  def numRecords(implicit tx: S#Tx): Int

  /**
   * Reports the current number of user records stored in the database.
   * That is the number of records minus those records used for
   * database maintenance.
   */
  def numUserRecords(implicit tx: S#Tx): Int

  def debugListUserRecords()(implicit tx: S#Tx): Seq[S#ID]

  private[stm] def read[@spec(ialized) A](id: Int)(valueFun: DataInput => A)(implicit tx: S#Tx): A

  private[stm] def tryRead[A](id: Long)(valueFun: DataInput => A)(implicit tx: S#Tx): Option[A] // Option currently not specialized?

  private[stm] def write(id: Int)(valueFun: DataOutput => Unit)(implicit tx: S#Tx): Unit

  private[stm] def write(id: Long)(valueFun: DataOutput => Unit)(implicit tx: S#Tx): Unit

  private[stm] def remove(id: Int)(implicit tx: S#Tx): Unit

  private[stm] def remove(id: Long)(implicit tx: S#Tx): Unit

  private[stm] def exists(id: Int)(implicit tx: S#Tx): Boolean

  private[stm] def exists(id: Long)(implicit tx: S#Tx): Boolean

  private[stm] def newIDValue()(implicit tx: S#Tx): Int

  def wrap(peer: InTxn): S#Tx  // XXX TODO this might go in Cursor?

  def inMemory: I
}

trait Durable extends DurableLike[Durable] {
  final type Tx = Durable.Txn
  final type I  = InMemory
}
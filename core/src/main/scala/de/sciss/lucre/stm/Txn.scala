/*
 *  Txn.scala
 *  (LucreSTM-Core)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package lucre
package stm

import concurrent.stm.{InTxn, Txn => ScalaTxn}
import serial.{DataInput, Serializer}

object TxnLike {
  /** Implicitly extracts a Scala STM transaction from a `TxnLike` instance. */
  implicit def peer(implicit tx: TxnLike): InTxn = tx.peer

  /** Implicitly treats a Scala STM transaction as a `TxnLike` instance. */
  implicit def wrap(implicit peer: InTxn): TxnLike = new Wrapped(peer)

  private final class Wrapped(val peer: InTxn) extends TxnLike {
    override def toString = peer.toString

    def afterCommit(code: => Unit): Unit = ScalaTxn.afterCommit(_ => code)(peer)
  }
}
/** This is a minimal trait for any type of transactions that wrap an underlying Scala-STM transaction. */
trait TxnLike {
  /** Every transaction has a plain Scala-STM transaction as a peer. This comes handy for
    * setting up custom things like `TxnLocal`, `TMap`, or calling into the hooks of `concurrent.stm.Txn`.
    * It is also needed when re-wrapping the transaction of one system into another.
    */
  def peer: InTxn

  /** Registers a thunk to be executed after the transaction successfully committed. */
  def afterCommit(code: => Unit): Unit

  // XXX TODO: Add this in the next major version
  // def beforeCommit(fun: S#Tx => Unit): Unit
}

trait Txn[S <: Sys[S]] extends TxnLike {
  /** Back link to the underlying system. */
  val system: S

  implicit def inMemory: S#I#Tx

  def newID(): S#ID

  def newVar[A]    (id: S#ID, init: A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]
  def newBooleanVar(id: S#ID, init: Boolean): S#Var[Boolean]
  def newIntVar    (id: S#ID, init: Int    ): S#Var[Int]
  def newLongVar   (id: S#ID, init: Long   ): S#Var[Long]

  def newVarArray[A](size: Int): Array[S#Var[A]]

  def newPartialID(): S#ID
  def newPartialVar[A](id: S#ID, init: A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]

  def newLocalVar[A](init: S#Tx => A): LocalVar[S#Tx, A]

  /** Creates a new durable transactional map for storing and retrieving values based on a mutable's identifier
    * as key. If a system is confluently persistent, the `get` operation will find the most recent key that
    * matches the search key.
    *
    * ID maps can be used by observing views to look up associated view meta data even though they may be
    * presented with a more recent access path of the model peer (e.g. when a recent event is fired and observed).
    *
    * @param serializer the serializer for values in the map
    * @tparam A         the value type in the map
    */
  def newDurableIDMap[A](implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A]

  /** Creates a new in-memory transactional map for storing and retrieving values based on a mutable's identifier
    * as key. If a system is confluently persistent, the `get` operation will find the most recent key that
    * matches the search key. Objects are not serialized but kept live in memory.
    *
    * ID maps can be used by observing views to look up associated view meta data even though they may be
    * presented with a more recent access path of the model peer (e.g. when a recent event is fired and observed).
    *
    * @tparam A         the value type in the map
    */
  def newInMemoryIDMap[A]: IdentifierMap[S#ID, S#Tx, A]

  def readVar[A]    (id: S#ID, in: DataInput)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]
  def readBooleanVar(id: S#ID, in: DataInput): S#Var[Boolean]
  def readIntVar    (id: S#ID, in: DataInput): S#Var[Int]
  def readLongVar   (id: S#ID, in: DataInput): S#Var[Long]

  def readPartialVar[A](id: S#ID, in: DataInput)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]

  def readID(in: DataInput, acc: S#Acc): S#ID

  def readPartialID(in: DataInput, acc: S#Acc): S#ID

  def readDurableIDMap[A](in: DataInput)(implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A]

  /** Creates a handle (in-memory) to refresh a stale version of an object, assuming that the future transaction is issued
    * from the same cursor that is used to create the handle, except for potentially having advanced.
    * This is a mechanism that can be used in live views to gain valid access to a referenced object
    * (e.g. self access).
    *
    * @param value         the object which will be refreshed when calling `get` on the returned handle
    * @param serializer    used to write and freshly read the object
    * @return              the handle
    */
  def newHandle[A](value: A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A]

  def beforeCommit(fun: S#Tx => Unit): Unit
  def afterCommit (fun:      => Unit): Unit
}
/*
 *  PersistentStore.scala
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

import serial.{DataInput, DataOutput}

trait DataStore {
  def put(   keyFun: DataOutput => Unit)(valueFun: DataOutput => Unit)(implicit tx: TxnLike): Unit
  def get[A](keyFun: DataOutput => Unit)(valueFun: DataInput => A)(    implicit tx: TxnLike): Option[A]
  def contains(keyFun: DataOutput => Unit)(implicit tx: TxnLike): Boolean
  def remove(  keyFun: DataOutput => Unit)(implicit tx: TxnLike): Boolean

  def flatGet[A](keyFun: DataOutput => Unit)(valueFun: DataInput => Option[A])(implicit tx: TxnLike) : Option[A]

  def numEntries(implicit tx: TxnLike): Int
  def close(): Unit
}

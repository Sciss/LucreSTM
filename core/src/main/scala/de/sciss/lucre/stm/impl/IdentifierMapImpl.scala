/*
 *  IdentifierMapImpl.scala
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
package impl

import concurrent.stm.TMap
import serial.DataOutput

object IdentifierMapImpl {
  def newInMemoryIntMap[ID, Tx <: TxnLike, A](id: ID)(implicit intView: ID => Int): IdentifierMap[ID, Tx, A] =
    new InMemoryInt[ID, Tx, A](id, intView)

  private final class InMemoryInt[ID, Tx <: TxnLike, A](val id: ID, intView: ID => Int)
    extends IdentifierMap[ID, Tx, A] {

    private val peer = TMap.empty[Int, A]

    def get(id: ID)(implicit tx: Tx): Option[A] = peer.get(intView(id))(tx.peer)

    def getOrElse(id: ID, default: => A)(implicit tx: Tx): A = get(id).getOrElse(default)

    def put(id: ID, value: A)(implicit tx: Tx): Unit =
      peer.put(intView(id), value)(tx.peer)

    def contains(id: ID)(implicit tx: Tx): Boolean = peer.contains(intView(id))(tx.peer)

    def remove(id: ID)(implicit tx: Tx): Unit =
      peer.remove(intView(id))(tx.peer)

    override def toString = "IdentifierMap"

    def write(out: DataOutput): Unit = ()
    def dispose()(implicit tx: Tx): Unit = ()
  }
}

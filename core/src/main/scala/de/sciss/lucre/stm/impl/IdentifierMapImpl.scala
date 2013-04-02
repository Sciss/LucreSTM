/*
 *  IdentifierMapImpl.scala
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

    def put(id: ID, value: A)(implicit tx: Tx) {
      peer.put(intView(id), value)(tx.peer)
    }

    def contains(id: ID)(implicit tx: Tx): Boolean = peer.contains(intView(id))(tx.peer)

    def remove(id: ID)(implicit tx: Tx) {
      peer.remove(intView(id))(tx.peer)
    }

    override def toString = "IdentifierMap"

    def write(out: DataOutput) {}
    def dispose()(implicit tx: Tx) {}
  }

}

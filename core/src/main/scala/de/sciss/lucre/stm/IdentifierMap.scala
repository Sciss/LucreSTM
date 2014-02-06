/*
 *  IdentifierMap.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
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

import impl.IdentifierMapImpl

object IdentifierMap {
  def newInMemoryIntMap[ID, Tx <: TxnLike, A](id: ID)(implicit intView: ID => Int): IdentifierMap[ID, Tx, A] =
    IdentifierMapImpl.newInMemoryIntMap[ID, Tx, A](id)
}
/**
 * An identifier map is basically a transactional map whose keys are system identifiers.
 * However, there are two important aspects: First, the map is always ephemeral
 * (but might be still durable!), even for a confluently persistent system. Second,
 * for systems whose identifiers constitute temporal traces (confluently persistent
 * system), lookup (via `get`, `contains` etc.) finds _any_ value stored for the
 * current version or any older version. That is to say, in a confluently persistent
 * system, it looks up the most recent entry for the key. It is therefore a useful
 * tool to map system entities to ephemeral live views.
 *
 * @tparam Tx  the underlying system's transaction type
 * @tparam ID  the underlying system's identifier type
 * @tparam A   the values stored at the keys. `Unit` can be used if only set
 *             functionality is needed.
 */
trait IdentifierMap[ID, -Tx, A] extends Mutable[ID, Tx] {
  def put      (id: ID, value: A)     (implicit tx: Tx): Unit
  def get      (id: ID)               (implicit tx: Tx): Option[A]
  def getOrElse(id: ID, default: => A)(implicit tx: Tx): A

  def contains (id: ID)(implicit tx: Tx): Boolean
  def remove   (id: ID)(implicit tx: Tx): Unit
}

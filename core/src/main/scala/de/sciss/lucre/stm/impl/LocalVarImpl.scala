/*
 *  LocalVarImpl.scala
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

package de.sciss.lucre.stm
package impl

import concurrent.stm.TxnLocal

final class LocalVarImpl[S <: Sys[S], A](init: S#Tx => A)
  extends LocalVar[S#Tx, A] {

  private val peer = TxnLocal[A]()

  override def toString = "TxnLocal<" + hashCode().toHexString + ">"

  def apply()(implicit tx: S#Tx): A = {
    implicit val itx = tx.peer
    if (peer.isInitialized) peer.get
    else {
      val initVal = init(tx)
      peer.set(initVal)
      initVal
    }
  }

  def update(v: A)(implicit tx: S#Tx) {
    peer.set(v)(tx.peer)
  }

  def isInitialized(implicit tx: S#Tx): Boolean = peer.isInitialized(tx.peer)
}

/*
 *  LocalVarImpl.scala
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

package de.sciss.lucre.stm
package impl

import concurrent.stm.TxnLocal

final class LocalVarImpl[S <: Sys[S], A](init: S#Tx => A)
  extends LocalVar[S#Tx, A] {

  private val peer = TxnLocal[A]()

  override def toString = s"TxnLocal<${hashCode().toHexString}>"

  def apply()(implicit tx: S#Tx): A = {
    implicit val itx = tx.peer
    if (peer.isInitialized) peer.get
    else {
      val initVal = init(tx)
      peer.set(initVal)
      initVal
    }
  }

  def update(v: A)(implicit tx: S#Tx): Unit =
    peer.set(v)(tx.peer)

  def isInitialized(implicit tx: S#Tx): Boolean = peer.isInitialized(tx.peer)
}

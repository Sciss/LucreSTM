/*
 *  BasicTxnImpl.scala
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

package de.sciss.lucre.stm
package impl

import concurrent.stm.Txn

trait BasicTxnImpl[S <: Sys[S]] extends Txn[S] {
  _: S#Tx =>

  def beforeCommit(fun: S#Tx => Unit): Unit =
    Txn.beforeCommit(_ => fun(this))(peer)

  def afterCommit(code: => Unit): Unit =
    Txn.afterCommit(_ => code)(peer)
}
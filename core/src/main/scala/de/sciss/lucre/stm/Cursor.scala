/*
 *  Cursor.scala
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

package de.sciss.lucre
package stm

trait Cursor[S <: Sys[S]] {
  def step[A](fun: S#Tx => A): A
  def position(implicit tx: S#Tx): S#Acc
}

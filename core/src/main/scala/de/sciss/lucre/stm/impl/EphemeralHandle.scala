/*
 *  EphemeralHandle.scala
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

package de.sciss.lucre
package stm
package impl

final class EphemeralHandle[Tx, A](value: A) extends Source[Tx, A] {
  override def toString = "handle: " + value

  def apply()(implicit tx: Tx): A = value
}

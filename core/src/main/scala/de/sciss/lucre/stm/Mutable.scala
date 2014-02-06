/*
 *  Mutable.scala
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

package de.sciss
package lucre
package stm

import serial.{DataOutput, Writable}

object Mutable {
  trait Impl[S <: Sys[S]] extends Mutable[S#ID, S#Tx] {
    final def dispose()(implicit tx: S#Tx): Unit = {
      id.dispose()
      disposeData()
    }

    final def write(out: DataOutput): Unit = {
      id.write(out)
      writeData(out)
    }

    protected def disposeData()(implicit tx: S#Tx): Unit
    protected def writeData(out: DataOutput): Unit

    // note: micro benchmark shows that an initial this eq that.asInstanceOf[AnyRef] doesn't improve performance at all
    override def equals(that: Any): Boolean = that match {
      case m: Mutable[_, _] =>
        id == m.id
      case _ => super.equals(that)
    }

    override def hashCode = id.hashCode()

    override def toString = super.toString + id.toString
  }
}
trait Mutable[+ID, -Tx] extends Identifiable[ID] with Writable with Disposable[Tx]

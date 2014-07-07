/*
 *  Mutable.scala
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

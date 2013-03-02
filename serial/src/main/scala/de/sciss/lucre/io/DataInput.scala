/*
 *  DataInput.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.io

import impl.ByteArrayInputStream
import java.io.{InputStream, DataInputStream}

object DataInput {
  def apply(buf: Array[Byte]): DataInput = apply(buf, 0, buf.length)
  def apply(buf: Array[Byte], off: Int, len: Int): DataInput = {
    val bin = new ByteArrayInputStream(buf, off, len)
    new Impl(bin)
  }

  private final class Impl(bin: ByteArrayInputStream) extends DataInputStream(bin) with DataInput {
    override def toString = s"DataInput(pos = $position, available = ${bin.available})@${hashCode().toHexString}"

    @inline def position    = bin.position
    @inline def position_=(value: Int) { bin.position = value }
    @inline def toByteArray = bin.toByteArray
    @inline def size        = bin.size
    @inline def buffer      = bin.buffer

    def asInputStream: InputStream = this
  }


}
trait DataInput extends java.io.DataInput with ByteArrayStream {
  def asInputStream: InputStream
}

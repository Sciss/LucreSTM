/*
 *  ByteArrayInputStream.scala
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

package de.sciss.lucre.io.impl

import java.io.InputStream
import de.sciss.lucre.io.ByteArrayStream

/** A non-synchronized alternative to `java.io.ByteArrayInputStream` */
final class ByteArrayInputStream(val buffer: Array[Byte], off: Int, val size: Int)
  extends InputStream with ByteArrayStream {

  private var _off = off

  def this(buf: Array[Byte]) = this(buf, 0, buf.length)

  def read(): Int = {
    if (_off >= size) return -1

    val b = buffer(_off) & 0xFF
    _off += 1
    b
  }

  def toByteArray: Array[Byte] = buffer.clone()

  override def read(out: Array[Byte], outOff: Int, outLen: Int): Int = {
    val rem = size - _off
    if (rem == 0) return -1
    val num = if (outLen > rem) rem else outLen

    System.arraycopy(buffer, _off, out, outOff, num)
    _off += num
    num
  }

  override def available: Int = size - _off

  def position: Int = _off
  def position_=(value: Int) {
    if (value < 0 || value > size) throw new IndexOutOfBoundsException(value.toString)
    _off = value
  }

  override def skip(n: Long): Long = {
    val rem = size - _off
    val res = if (n > rem) rem else n.toInt
    _off += res
    res
  }
}
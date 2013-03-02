package de.sciss.lucre
package io

import org.scalatest.FunSuite

class IOSuite extends FunSuite {
  test("serialization and deserialization is run on various primitives") {
    val dout = DataOutput()
    dout.writeByte(1)
    dout.writeByte(-1)
    dout.writeShort(2)
    dout.writeShort(-2)
    dout.writeInt(3)
    dout.writeInt(-3)
    dout.writeLong(4)
    dout.writeLong(-4)
    dout.write(Array[Byte](5,6,7))
    assert(dout.size === 33)
    dout.writeUTF("")         // +2
    dout.writeUTF("foo.bar")  // +9
    dout.writeBoolean(true)
    dout.writeBoolean(false)
    assert(dout.size === 46)
    val doutA = dout.toByteArray
    assert(doutA.length === 46)

    val din = DataInput(doutA)
    assert(din.readByte() === 1)
    assert(din.readByte() === -1)
    assert(din.readShort() === 2)
    assert(din.readShort() === -2)
    assert(din.readInt() === 3)
    assert(din.readInt() === -3)
    assert(din.readLong() === 4)
    assert(din.readLong() === -4)
    val arr = new Array[Byte](3)
    din.readFully(arr)
    assert(arr === Array[Byte](5,6,7))
    assert(din.readUTF() === "")
    assert(din.readUTF() === "foo.bar")
    assert(din.readBoolean() === true)
    assert(din.readBoolean() === false)
  }
}
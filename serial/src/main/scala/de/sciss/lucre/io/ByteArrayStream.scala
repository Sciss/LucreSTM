package de.sciss.lucre.io

trait ByteArrayStream {
  def toByteArray: Array[Byte]
  def reset(): Unit
  def size: Int
  def buffer: Array[Byte]
  var position: Int
}
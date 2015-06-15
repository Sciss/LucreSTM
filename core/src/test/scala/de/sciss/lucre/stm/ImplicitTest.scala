package de.sciss.lucre.stm

import de.sciss.serial.Serializer

object ImplicitTest {
  def test1[S <: Sys[S]](): Unit = implicitly[Serializer[S#Tx, S#Acc, Double]]

  def test2(): Unit = {
    type S = InMemory
    implicitly[Serializer[S#Tx, S#Acc, Double]]
  }
}

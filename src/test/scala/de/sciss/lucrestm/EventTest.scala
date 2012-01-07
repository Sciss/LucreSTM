package de.sciss.lucrestm

import fluent.Confluent

object EventTest extends App {
   val system  = Confluent()
   type S      = Confluent

   val e = system.atomic { implicit tx => Event.Bang[ S ]() }

   system.atomic { implicit tx => e.observe { (tx, _) =>
      println( "Bang!" )
   }}

   system.atomic { implicit tx =>
      e.fire()
   }
}
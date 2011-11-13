package de.sciss.lucrestm

import java.io.{ObjectInputStream, ObjectOutputStream}

object Serializer {
   implicit object Int extends Serializer[ scala.Int ] {
      def write( v: scala.Int, os: ObjectOutputStream ) {
         os.writeInt( v )
      }
      def read( is: ObjectInputStream ) : scala.Int = is.readInt()
   }
}
trait Serializer[ @specialized A ] {
   def write( v: A, os: ObjectOutputStream ) : Unit
   def read( is: ObjectInputStream ) : A
}
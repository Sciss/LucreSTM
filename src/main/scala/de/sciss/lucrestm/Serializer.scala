package de.sciss.lucrestm

object Serializer {
   implicit object Int extends Serializer[ scala.Int ] {
      def write( v: scala.Int, out: DataOutput ) {
         out.writeInt( v )
      }
      def read( in: DataInput ) : scala.Int = in.readInt()
   }
}
trait Serializer[ @specialized A ] {
   def write( v: A, out: DataOutput ) : Unit
   def read( in: DataInput ) : A
}
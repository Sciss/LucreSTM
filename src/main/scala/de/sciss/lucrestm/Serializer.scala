package de.sciss.lucrestm

trait Writer[ +A ] {
   def write( out: DataOutput ) : Unit
}

trait Reader[ @specialized +A ] {
   def read( in: DataInput ) : A
}

object Serializer {
   implicit object Int extends Serializer[ scala.Int ] {
      def write( v: scala.Int, out: DataOutput ) {
         out.writeInt( v )
      }
      def read( in: DataInput ) : scala.Int = in.readInt()
   }

   implicit def fromReader[ A <: Writer[ A ]]( implicit reader: Reader[ A ]) : Serializer[ A ] = new ReaderWrapper( reader )

   private final class ReaderWrapper[ A <: Writer[ A ]]( reader: Reader[ A ]) extends Serializer[ A ] {
      def write( v: A, out: DataOutput ) { v.write( out )}
      def read( in: DataInput ) : A = reader.read( in )
   }
}
trait Serializer[ @specialized A ] extends Reader[ A ] {
   def write( v: A, out: DataOutput ) : Unit
//   def read( in: DataInput ) : A
}
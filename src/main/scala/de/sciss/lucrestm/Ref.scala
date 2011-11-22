package de.sciss.lucrestm

sealed trait Sink[ -Tx, -A ] {
   def set( v: A )( implicit tx: Tx ) : Unit
}

sealed trait Source[ -Tx, +A ] extends Writer[ A ] with Disposable[ Tx ] {
   def get( implicit tx: Tx ) : A
}

trait Ref[ -Tx, A ] extends Sink[ Tx, A ] with Source[ Tx, A ] {
   def transform( f: A => A )( implicit tx: Tx ) : Unit
   def debug() : Unit
}

//object Mutable {
//   case object Empty extends Mutable[ Any, Nothing ] {
//      def isEmpty   = true
//      def isDefined = false
//      def get( implicit tx: Any ) : Nothing = sys.error( "Get on an empty mutable" )
//      def dispose()( implicit tx: Any ) {}
//      def write( out: DataOutput ) {}
//   }
//}
trait Mutable[ -Tx, +A ] extends Source[ Tx, A ] /* with Writer[ A ] */ /* with Disposable[ Tx ] */ {
   def isEmpty : Boolean
   def isDefined : Boolean
//   def orNull( implicit tx: Tx ) : A
//   def write( out: DataOutput ) : Unit
   def orNull[ A1 >: A ]( implicit tx: Tx, ev: <:<[ Null, A1 ]) : A1
}
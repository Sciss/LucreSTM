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

trait Mutable[ -Tx, A ] extends Source[ Tx, A ] {
//   def write( out: DataOutput ) : Unit
}
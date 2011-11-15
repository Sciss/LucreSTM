package de.sciss.lucrestm

trait Sink[ Txn, -A ] {
   def set( v: A )( implicit tx: Txn ) : Unit
}

trait Source[ Txn, +A ] {
   def get( implicit tx: Txn ) : A
}

trait Ref[ Txn, A ] extends Sink[ Txn, A ] with Source[ Txn, A ] {
   def debug() : Unit
}
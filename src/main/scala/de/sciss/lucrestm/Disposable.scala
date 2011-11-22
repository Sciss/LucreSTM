package de.sciss.lucrestm

trait Disposable[ -Tx ] {
   def dispose()( implicit tx: Tx ) : Unit
}
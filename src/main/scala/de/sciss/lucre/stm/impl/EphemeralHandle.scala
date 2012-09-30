package de.sciss.lucre
package stm
package impl

final class EphemeralHandle[ Tx, A ]( value: A ) extends Source[ Tx, A ] {
   override def toString = "handle: " + value
   def get( implicit tx: Tx ) : A = value
}

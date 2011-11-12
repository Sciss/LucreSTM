package de.sciss.lucrestm

trait Ref[ A, S <: Sys[ S ]] {
   def set( v: A )( implicit tx: S#Tx ) : Unit
   def get( implicit tx: S#Tx ) : A
}
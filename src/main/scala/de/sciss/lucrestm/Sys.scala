package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => STMRef}

trait Sys[ Self <: Sys[ Self ]] {
   type Ref[ A ] <: STMRef[ A, Self ]
   type Tx

   def newRef[ A : Serializer ]( init: A ) : Self#Ref[ A ]
   def disposeRef[ A ]( ref: Self#Ref[ A ]) : Unit
}
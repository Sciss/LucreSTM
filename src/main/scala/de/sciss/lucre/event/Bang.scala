package de.sciss.lucre
package event

import stm.Sys

object Bang {
   def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Bang[ S ] = new Impl[ S ] {
      protected val targets = Invariant.Targets[ S ]
   }

   private sealed trait Impl[ S <: Sys[ S ]] extends Bang[ S ] with Singleton[ S ] with Root[ S, Unit /*, Bang[ S ] */] {
      protected def reader = Bang.serializer[ S ]
   }

   def serializer[ S <: Sys[ S ]] : Invariant.Serializer[ S, Bang[ S ]] = new Invariant.Serializer[ S, Bang[ S ]] {
      def read( in: DataInput, access: S#Acc, _targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Bang[ S ] =
         new Impl[ S ] {
            protected val targets = _targets
         }
   }
}

/**
 * A simple event implementation for an imperative (trigger) event that fires "bangs" or impulses, using the
 * `Unit` type as event type parameter. The `apply` method of the companion object builds a `Bang` which also
 * implements the `Observable` trait, so that the bang can be connected to a live view (e.g. a GUI).
 */
trait Bang[ S <: Sys[ S ]] extends Trigger.Impl[ S, Unit, Unit, Bang[ S ]] with StandaloneLike[ S, Unit, Bang[ S ]] {
   /**
    * A parameterless convenience version of the `Trigger`'s `apply` method.
    */
   def apply()( implicit tx: S#Tx ) { apply( () )}

   override def toString = "Bang"
}

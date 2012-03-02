package de.sciss.lucre
package event

import stm.{Disposable, Sys}


object Observer {
   def apply[ S <: Sys[ S ], A, Repr ](
      reader: Reader[ S, Repr, _ ], fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] = {

      val key = tx.reactionMap.addEventReaction[ A, Repr ]( reader, fun )
      new Impl[ S, A, Repr ]( key )
   }

   private final class Impl[ S <: Sys[ S ], A, Repr ](
      key: ObserverKey[ S ])
   extends Observer[ S, A, Repr ] {
      override def toString = "Observer<" + key.id + ">"

      def add( event: Event[ S, _ <: A, Repr ])( implicit tx: S#Tx ) {
         event ---> key
      }

      def remove( event: Event[ S, _ <: A, Repr ])( implicit tx: S#Tx ) {
         event -/-> key
      }

      def dispose()( implicit tx: S#Tx ) {
         tx.reactionMap.removeEventReaction( key )
      }
   }

   def dummy[ S <: Sys[ S ], A, Repr ] : Observer[ S, A, Repr ] = new Dummy[ S, A, Repr ]

   private final class Dummy[ S <: Sys[ S ], A, Repr ] extends Observer[ S, A, Repr ] {
      def add( event: Event[ S, _ <: A, Repr ])( implicit tx: S#Tx ) {}
      def remove( event: Event[ S, _ <: A, Repr ])( implicit tx: S#Tx ) {}
      def dispose()( implicit tx: S#Tx ) {}
   }
}

/**
 * `Observer` instances are returned by the `observe` method of classes implementing
 * `Observable`. The observe can be registered and unregistered with events.
 */
sealed trait Observer[ S <: Sys[ S ], A, Repr ] extends Disposable[ S#Tx ] {
   def add(    event: Event[ S, _ <: A, Repr ])( implicit tx: S#Tx ) : Unit
   def remove( event: Event[ S, _ <: A, Repr ])( implicit tx: S#Tx ) : Unit
}
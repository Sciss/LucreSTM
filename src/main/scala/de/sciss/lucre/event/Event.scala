/*
 *  Event.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre
package event

import stm.Sys

/* sealed */ trait EventLike[ S <: Sys[ S ], A, Repr ] {
   /**
    * Connects the given selector to this event. That is, this event will
    * adds the selector to its propagation targets.
    */
   private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Unit
   /**
    * Disconnects the given selector from this event. That is, this event will
    * remove the selector from its propagation targets.
    */
   private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Unit

   /**
    * Registers a live observer with this event. The method is called with the
    * observing function which receives the event's update messages, and the
    * method generates an opaque `Observer` instance, which may be used to
    * remove the observer eventually (through the observer's `remove` method),
    * or to add the observer to other events of the same type (using the
    * observer's `add` method).
    *
    * Note that the caller should not call `add`
    * on the resulting observer to register this event, as this is already
    * done as part of the call to `react`.
    */
   def react( fun: A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ]

   /**
    * Like `react`, but passing in a transaction as first function argument.
    */
   def reactTx( fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ]

   /**
    * Tests whether this event participates in a pull. That is, whether the
    * event was visited during the push phase.
    */
   private[lucre] def isSource( pull: Pull[ S ]) : Boolean

   /**
    * Called when the first target is connected to the underlying dispatcher node. This allows
    * the event to be lazily added to its sources. A lazy event (most events should be lazy)
    * should call invoke `source ---> this` for each event source. A strict event, an event
    * without sources, or a collection event may simply ignore this method by providing a
    * no-op implementation.
    */
   private[lucre] def connect()( implicit tx: S#Tx ) : Unit

   /**
    * The counterpart to `connect` -- called when the last target is disconnected from the
    * underlying dispatcher node. Events participating in lazy source registration should use
    * this call to detach themselves from their sources, e.g. call `source -/-> this` for
    * each event source. All other events may ignore this method by providing a
    * no-op implementation.
    */
   private[lucre] def disconnect()( implicit tx: S#Tx ) : Unit

   /**
    * Involves this event in the pull-phase of event delivery. The event should check
    * the source of the originally fired event, and if it identifies itself with that
    * source, cast the `update` to the appropriate type `A` and wrap it in an instance
    * of `Some`. If this event is not the source, it should invoke `pull` on any
    * appropriate event source feeding this event.
    *
    * @return  the `update` as seen through this event, or `None` if the event did not
    *          originate from this part of the dependency graph or was absorbed by
    *          a filtering function
    */
   private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]
}

object Dummy {
   def apply[ S <: Sys[ S ], A, Repr ] : Dummy[ S, A, Repr ] = new Dummy[ S, A, Repr ] {}

   private def opNotSupported = sys.error( "Operation not supported ")
}
trait Dummy[ S <: Sys[ S ], A, Repr ] extends EventLike[ S, A, Repr ] {
   import Dummy._

   final private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {}
   final private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {}

//   final private[lucre] def select() : NodeSelector[ S ] = opNotSupported

   /**
    * Returns `false`, as a dummy is never a source event.
    */
   final private[lucre] def isSource( pull: Pull[ S ]) = false

   final def react( fun: A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] =
      Observer.dummy[ S, A, Repr ]

   final def reactTx( fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] =
      Observer.dummy[ S, A, Repr ]

   final private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ] = opNotSupported

   final private[lucre] def connect()(    implicit tx: S#Tx ) {}
   final private[lucre] def disconnect()( implicit tx: S#Tx ) {}
}

/**
 * `Event` is not sealed in order to allow you define traits inheriting from it, while the concrete
 * implementations should extend either of `Event.Constant` or `Event.Node` (which itself is sealed and
 * split into `Event.Invariant` and `Event.Mutating`.
 */
trait Event[ S <: Sys[ S ], A, Repr ] extends EventLike[ S, A, Repr ] with NodeSelector[ S, A ]

trait InvariantEvent[ S <: Sys[ S ], A, Repr ] extends Event[ S, A, Repr ] with InvariantSelector[ S ] {
   final private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      val t = reactor._targets
      if( t.add( slot, r )) {
         connect()
      } else if( t.isInvalid( slot )) {
         // XXX TODO -- could be a more efficient reconnect() method at some point
         disconnect()
         connect()
      }
   }

   final private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      if( reactor._targets.remove( slot, r )) disconnect()
   }
}

trait MutatingEvent[ S <: Sys[ S ], A, Repr ] extends Event[ S, A, Repr ] with MutatingSelector[ S ] {
   final private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      reactor._targets.add( slot, r )
   }

   final private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      reactor._targets.remove( slot, r )
   }

   final private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ] = {
      pull.clearInvalid( this )
      processUpdate( pull )
   }

   protected def processUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]
}
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

import stm.{InMemory, Sys, Serializer}
import LucreSTM.logEvent
import util.MurmurHash

object Selector {
   implicit def serializer[ S <: Sys[ S ]] : Serializer[ S#Tx, S#Acc, Selector[ S ]] = new Ser[ S ]

   private[event] def apply[ S <: Sys[ S ]]( slot: Int, node: VirtualNode.Raw[ S ],
                                             invariant: Boolean ) : VirtualNodeSelector[ S ] = {
      if( invariant ) InvariantTargetsSelector( slot, node )
      else            MutatingTargetsSelector(  slot, node )
   }

   private final class Ser[ S <: Sys[ S ]] extends Serializer[ S#Tx, S#Acc, Selector[ S ]] {
      def write( v: Selector[ S ], out: DataOutput ) {
         v.writeSelector( out )
      }

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Selector[ S ] = {
         val cookie = in.readUnsignedByte()
         // 0 = invariant, 1 = mutating, 2 = observer
         if( cookie == 0 || cookie == 1 ) {
            val slot    = in.readInt()
// MMM
//            val reactor = Targets.readAndExpand[ S ]( in, access )
val fullSize = in.readInt()
val reactor  = VirtualNode.read[ S ]( in, fullSize, access )
            reactor.select( slot, cookie == 0 )
         } else if( cookie == 2 ) {
            val id = in.readInt()
            new ObserverKey[ S ]( id )
         } else {
            sys.error( "Unexpected cookie " + cookie )
         }
      }
   }

   private sealed trait TargetsSelector[ S <: Sys[ S ]] extends VirtualNodeSelector[ S ] {
//      override protected def reactor: Targets[ S ]
//      override private[event] def reactor: Targets[ S ]
//      protected def data: Array[ Byte ]
//      protected def access: S#Acc
      override private[lucre] def node: VirtualNode.Raw[ S ]

//      final private[event] def nodeSelectorOption: Option[ NodeSelector[ S, Any ]] = None

//      final protected def writeVirtualNode( out: DataOutput ) {
//         reactor.write( out )
//         out.write( data )
//      }

//      final def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : NodeSelector[ S, Any ] = {
//         node.devirtualize( reader ).select( slot, cookie == 0 )
//      }

      final def devirtualize[ Evt <: Event[ S, Any, Any ]]( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : Evt = {
         node.devirtualize( reader ).select( slot, cookie == 0 ).asInstanceOf[ Evt ]
      }
   }

   private final case class InvariantTargetsSelector[ S <: Sys[ S ]]( slot: Int, node: VirtualNode.Raw[ S ])
   extends TargetsSelector[ S ] with InvariantSelector[ S ]

   private final case class MutatingTargetsSelector[ S <: Sys[ S ]]( slot: Int, node: VirtualNode.Raw[ S ])
   extends TargetsSelector[ S ] with MutatingSelector[ S ]
}

sealed trait Selector[ S <: Sys[ S ]] /* extends Writable */ {
   protected def cookie: Int

   final def writeSelector( out: DataOutput ) {
      out.writeUnsignedByte( cookie )
      writeSelectorData( out )
   }

   protected def writeSelectorData( out: DataOutput ) : Unit

   private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) : Unit // ( implicit tx: S#Tx ) : Unit
   private[event] def toObserverKey : Option[ ObserverKey[ S ]] // Option[ Int ]
}

sealed trait VirtualNodeSelector[ S <: Sys[ S ]] extends Selector[ S ] {
//   private[event] def reactor: Reactor[ S ]

   private[lucre] def node: VirtualNode[ S ]
   private[event] def slot: Int

//   private[event] def nodeSelectorOption: Option[ NodeSelector[ S, Any ]]
   final protected def writeSelectorData( out: DataOutput ) {
      out.writeInt( slot )
      val sizeOffset = out.getBufferLength
      out.writeInt( 0 ) // will be overwritten later -- note: addSize cannot be used, because the subsequent write will be invalid!!!
      node.write( out )
      val stop       = out.getBufferLength
      val delta      = stop - sizeOffset
      out.addSize( -delta )      // XXX ugly ... should have a seek method
      val fullSize   = delta - 4
      out.writeInt( fullSize )
      out.addSize( fullSize )
   }

//   private[lucre] def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : NodeSelector[ S, Any ]
   def devirtualize[ Evt <: Event[ S, Any, Any ]]( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : Evt

// MMM
//   final protected def writeSelectorData( out: DataOutput ) {
//      out.writeInt( slot )
//      reactor.id.write( out )
//   }

   override def hashCode : Int = {
      import MurmurHash._
      var h = startHash( 2 )
      val c = startMagicA
      val k = startMagicB
      h = extendHash( h, slot, c, k )
      h = extendHash( h, node.id.##, nextMagicA( c ), nextMagicB( k ))
      finalizeHash( h )
   }

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ VirtualNodeSelector[ _ ]]) {
         val thatSel = that.asInstanceOf[ VirtualNodeSelector[ _ ]]
         (slot == thatSel.slot && node.id == thatSel.node.id)
      } else super.equals( that ))
   }

   final private[event] def toObserverKey : Option[ ObserverKey[ S ]] = None

   override def toString = node.toString + ".select(" + slot + ")"
}

// MMM
//sealed trait ExpandedSelector[ S <: Sys[ S ]] extends Selector[ S ] /* with Writable */ {
//// MMM
////   private[event] def writeValue()( implicit tx: S#Tx ) : Unit
//}

///* sealed */ trait NodeSelector[ S <: Sys[ S ], +A ] extends VirtualNodeSelector[ S ] /* MMM with ExpandedSelector[ S ] */ {
//   private[lucre] def node: Node[ S ]
//
////   final private[event] def nodeSelectorOption: Option[ NodeSelector[ S, Any ]] = Some( this )
////   final protected def writeSelectorData( out: DataOutput ) {
////      out.writeInt( slot )
////      val sizeOffset = out.getBufferOffset
////      out.addSize( 4 )
////      reactor.write( out )
////      val pos        = out.getBufferOffset
////      val delta      = pos - sizeOffset
////      out.addSize( -delta )
////   }
//
//   final private[lucre] def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : NodeSelector[ S, Any ] =
//      this
//
//   /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]
//
//// MMM
////   final private[event] def writeValue()( implicit tx: S#Tx ) {
////      tx.writeVal[ VirtualNode[ S ]]( reactor.id, reactor ) // ( new Targets.ExpanderSerializer[ S ])
////   }
//}

trait InvariantSelector[ S <: Sys[ S ]] extends VirtualNodeSelector[ S ] {
   final protected def cookie: Int = 0

   final private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) {
      push.visit( this, parent )
   }
}

trait MutatingSelector[ S <: Sys[ S ]] extends VirtualNodeSelector[ S ] {
   final protected def cookie: Int = 1

//   final private[event] def _invalidate()( implicit tx: S#Tx ) {
//      reactor._targets.invalidate( slot )
//   }

//   final /* protected */ def invalidate()( implicit tx: S#Tx ) {
////      _invalidate()
//      reactor._targets.invalidate( slot )
//   }
//   final /* protected */ def isInvalid( implicit tx: S#Tx ) : Boolean = reactor._targets.isInvalid( slot )
//   final /* protected */ def validated()( implicit tx: S#Tx ) { reactor._targets.validated( slot )}

   final private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) {
      push.markInvalid( this )
      push.visit( this, parent )
   }
}

/**
 * Instances of `ObserverKey` are provided by methods in `Txn`, when a live `Observer` is registered. Since
 * the observing function is not persisted, the slot will be used for lookup (again through the transaction)
 * of the reacting function during the first reaction gathering phase of event propagation.
 */
final case class ObserverKey[ S <: Sys[ S ]] private[lucre] ( id: Int ) extends /* MMM Expanded */ Selector[ S ] {
   protected def cookie: Int = 2

   private[event] def toObserverKey : Option[ ObserverKey[ S ]] = Some( this )

   private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) {
//      val reader  = push
//      val nParent = parent.devirtualize( reader )
////
////      val nParent = parent.nodeSelectorOption.getOrElse(
////         sys.error( "Orphan observer " + this + " - no expanded node selector" )
////      )
      push.addLeaf( this, parent )
   }

// MMM
//   private[event] def writeValue()( implicit tx: S#Tx ) {}  // we are light weight, nothing to do here

   def dispose()( implicit tx: S#Tx ) {}  // XXX really?

   protected def writeSelectorData( out: DataOutput ) {
      out.writeInt( id )
   }
}

/* sealed */ trait EventLike[ S <: Sys[ S ], +A, +Repr ] {
   /**
    * Connects the given selector to this event. That is, this event will
    * adds the selector to its propagation targets.
    */
   /* private[lucre] */ def --->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) : Unit
   /**
    * Disconnects the given selector from this event. That is, this event will
    * remove the selector from its propagation targets.
    */
   /*private[lucre] */ def -/->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) : Unit

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
   def react[ A1 >: A ]( fun: A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ]

   /**
    * Like `react`, but passing in a transaction as first function argument.
    */
   def reactTx[ A1 >: A ]( fun: S#Tx => A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ]

   /**
    * Tests whether this event participates in a pull. That is, whether the
    * event was visited during the push phase.
    */
   /* private[lucre] */ def isSource( pull: Pull[ S ]) : Boolean

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

//   private[lucre] def reconnect()( implicit tx: S#Tx ) : Unit

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
   /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]
}

object Dummy {
   /**
    * This method is cheap.
    */
   def apply[ S <: Sys[ S ], A, Repr ] : Dummy[ S, A, Repr ] = anyDummy.asInstanceOf[ Dummy[ S, A, Repr ]]

   private val anyDummy = new Impl[ InMemory ]

   private final class Impl[ S <: Sys[ S ]] extends Dummy[ S, Any, Any ] {
      override def toString = "event.Dummy"
   }

   private def opNotSupported = sys.error( "Operation not supported ")
}
trait Dummy[ S <: Sys[ S ], +A, +Repr ] extends EventLike[ S, A, Repr ] {
   import Dummy._

   final /* private[lucre] */ def --->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {}
   final /* private[lucre] */ def -/->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {}

//   final private[lucre] def select() : NodeSelector[ S ] = opNotSupported

   /**
    * Returns `false`, as a dummy is never a source event.
    */
   final /* private[lucre] */ def isSource( pull: Pull[ S ]) = false

   final def react[ A1 >: A ]( fun: A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] =
      Observer.dummy[ S, A1, Repr ]

   final def reactTx[ A1 >: A ]( fun: S#Tx => A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] =
      Observer.dummy[ S, A1, Repr ]

   final /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ] = opNotSupported

   final private[lucre] def connect()(    implicit tx: S#Tx ) {}
//   final private[lucre] def reconnect()(  implicit tx: S#Tx ) {}
   final private[lucre] def disconnect()( implicit tx: S#Tx ) {}
}

/**
 * `Event` is not sealed in order to allow you define traits inheriting from it, while the concrete
 * implementations should extend either of `Event.Constant` or `Event.Node` (which itself is sealed and
 * split into `Event.Invariant` and `Event.Mutating`.
 */
trait Event[ S <: Sys[ S ], +A, +Repr ] extends EventLike[ S, A, Repr ] with VirtualNodeSelector[ S ] { // with NodeSelector[ S, A ]
   private[lucre] def node: Node[ S ]
   final def devirtualize[ Evt <: Event[ S, Any, Any ]]( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : Evt =
      this.asInstanceOf[ Evt ]

//   final private[lucre] def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : Event[ S, A, Repr ] = this
}

trait InvariantEvent[ S <: Sys[ S ], +A, +Repr ] extends InvariantSelector[ S ] with Event[ S, A, Repr ] {
   final /* private[lucre] */ def --->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {
      val t = node._targets
//      if( t.add( slot, r )) {
//         logEvent( this.toString + " connect" )
//         connect()
//      } else if( t.isInvalid( slot )) {
//         logEvent( this.toString + " re-connect" )
//         disconnect()
//         connect()
//         t.validated( slot )
//      }
      if( t.isInvalid( slot )) {
         logEvent( this.toString + " re-connect" )
         disconnect()
         t.resetAndValidate( slot, r )
         connect()
      } else if( t.add( slot, r )) {
         logEvent( this.toString + " connect" )
         connect()
      }
   }

   final /* private[lucre] */ def -/->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {
      if( node._targets.remove( slot, r )) disconnect()
   }
}

trait MutatingEvent[ S <: Sys[ S ], +A, +Repr ] extends MutatingSelector[ S ] with Event[ S, A, Repr ] {
   final /* private[lucre] */ def --->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {
      node._targets.add( slot, r )
   }

   final /* private[lucre] */ def -/->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {
      node._targets.remove( slot, r )
   }

   final /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ] = {
      pull.clearInvalid( this )
      processUpdate( pull )
   }

   protected def processUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]
}
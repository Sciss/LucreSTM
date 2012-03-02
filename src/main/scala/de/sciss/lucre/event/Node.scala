/*
 *  Node.scala
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

import collection.immutable.{IndexedSeq => IIdxSeq}
import stm.{TxnReader, TxnSerializer, Sys, Writer, Disposable}

/**
 * An abstract trait uniting invariant and mutating readers.
 */
/* sealed */ trait Reader[ S <: Sys[ S ], +Repr ] {
   def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Repr
}

trait NodeSerializer[ S <: Sys[ S ], Repr <: /* Writer */ Invariant[ S, _ ]]
extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
   final def write( v: Repr, out: DataOutput ) { v.write( out )}

   def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
      val targets = Targets.read[ S ]( in, access )
      read( in, access, targets )
   }
}

object Targets {
   def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
      val id         = tx.newID()
      val children   = tx.newVar[ Children[ S ]]( id, NoChildren )
      new Impl( id, children )
   }

   private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
      val id         = tx.readID( in, access )
      tx.readVal( id )( new ExpanderReader[ S ])
   }

   private class ExpanderReader[ S <: Sys[ S ]] extends TxnReader[ S#Tx, S#Acc, Reactor[ S ]] {
      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
         val targets    = Targets.read( in, access )
         val observers  = targets.childrenVar.get.flatMap( _._2.toObserverKey )
         tx.reactionMap.mapEventTargets( in, access, targets, observers )
      }
   }

   private[lucre] def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
      val cookie = in.readUnsignedByte()
      require( cookie == 0, "Unexpected cookie " + cookie )
      readIdentified( in, access )
   }

   private[event] def readIdentified[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
      val id            = tx.readID( in, access )
      val children      = tx.readVar[ Children[ S ]]( id, in )
      new Impl[ S ]( id, children )
   }

   private[event] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ Children[ S ]]) : Targets[ S ] =
      new Impl( id, children )

   private final class Impl[ S <: Sys[ S ]](
      val id: S#ID, protected val childrenVar: S#Var[ Children[ S ]])
   extends Targets[ S ] {
      def write( out: DataOutput ) {
         out.writeUnsignedByte( 0 )
         id.write( out )
         childrenVar.write( out )
      }

      def dispose()( implicit tx: S#Tx ) {
         require( !isConnected, "Disposing a event reactor which is still being observed" )
         id.dispose()
         childrenVar.dispose()
      }

      def select( key: Int ) : ReactorSelector[ S ] = Selector( key, this )
   }
}

/**
 * An abstract trait unifying invariant and mutating targets. This object is responsible
 * for keeping track of the dependents of an event source which is defined as the outer
 * object, sharing the same `id` as its targets. As a `Reactor`, it has a method to
 * `propagate` a fired event.
 */
sealed trait Targets[ S <: Sys[ S ]] extends Reactor[ S ] /* extends Writer with Disposable[ S#Tx ] */ {
   /* private[event] */ def id: S#ID

   protected def childrenVar: S#Var[ Children[ S ]]

   override def toString = "Targets" + id

   final private[event] def children( implicit tx: S#Tx ) : Children[ S ] = childrenVar.get

   final private[event] def add( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean = {
      val tup  = (outlet, sel)
      val old  = childrenVar.get
      sel.writeValue()
      childrenVar.set( old :+ tup )
      old.isEmpty
   }

   final private[event] def remove( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean = {
      val tup  = (outlet, sel)
      val xs   = childrenVar.get
      val i    = xs.indexOf( tup )
      if( i >= 0 ) {
         val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
         childrenVar.set( xs1 )
         xs1.isEmpty
      } else false
   }

   final def isConnected( implicit tx: S#Tx ) : Boolean = childrenVar.get.nonEmpty
}

/**
 * An `Event.Node` is most similar to EScala's `EventNode` class. It represents an observable
 * object and can also act as an observer itself. It adds the `Reactor` functionality in the
 * form of a proxy, forwarding to internally stored `Targets`. It also provides a final
 * implementation of the `Writer` and `Disposable` traits, asking sub classes to provide
 * methods `writeData` and `disposeData`. That way it is ensured that the sealed `Reactor` trait
 * is written first as the `Targets` stub, providing a means for partial deserialization during
 * the push phase of event propagation.
 *
 * This trait also implements `equals` and `hashCode` in terms of the `id` inherited from the
 * targets.
 */
/* sealed */ trait Node[ S <: Sys[ S ], A ] extends Reactor[ S ] /* with Dispatcher[ S, A ] */ {
   override def toString = "Node" + id

   protected def targets: Targets[ S ]
   protected def writeData( out: DataOutput ) : Unit
   protected def disposeData()( implicit tx: S#Tx ) : Unit

   final private[event] def children( implicit tx: S#Tx ) = targets.children

   private[event] def select( inlet: Int ) : NodeSelector[ S ]

   protected def connectNode()(    implicit tx: S#Tx ) : Unit
   protected def disconnectNode()( implicit tx: S#Tx ) : Unit

   private[event] def addTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Unit
   private[event] def removeTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Unit

   private[event] def getEvent( key: Int ) : Event[ S, _ <: A, _ ]

   final def id: S#ID = targets.id

   final def write( out: DataOutput ) {
      targets.write( out )
      writeData( out )
   }
}

//object Invariant {
//
//   trait Reader[ S <: Sys[ S ], +Repr ] extends event.Reader[ S, Repr, Targets[ S ]]
//
//   /**
//    * A trait to serialize events which are immutable nodes.
//    * An implementation mixing in this trait just needs to implement
//    * `read` with the `Event.Targets` argument to return the node instance.
//    */
//   trait Serializer[ S <: Sys[ S ], Repr <: /* Writer */ Invariant[ S, _ ]]
//   extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
//      final def write( v: Repr, out: DataOutput ) { v.write( out )}
//
//      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
//         val targets = Targets.read[ S ]( in, access )
//         read( in, access, targets )
//      }
//   }
//}

/**
 * An event which is `Invariant` designates a `Node` which does not mutate any internal state
 * as a result of events bubbling up from its sources. As a consequence, if an event is
 * propagated through this invariant event, and there are no live reactions currently hanging
 * off its target tree, the event can simply be swallowed without damage. If this event was
 * changing internal state, a loss of incoming events would be disastrous, as no live reactions
 * mean that the node's `Targets` are not fully deserialized into the outer `Node` object!
 * For such a situation, the invalidating `Mutating` node must be used.
 *
 * Most event nodes should be invariant, including combinators in expression systems, or
 * mapping, filtering and forwarding nodes.
 */
trait Invariant[ S <: Sys[ S ], A ] extends Node[ S, A ] {
   protected def targets: Targets[ S ]

   final def select( key: Int ) : NodeSelector[ S ] = Selector( key, this )

   final private[event] def addTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      if( targets.add( outlet, sel )) {
         connectNode()
      }
   }

   final private[event] def removeTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      if( targets.remove( outlet, sel )) {
         disconnectNode()
      }
   }

   final def dispose()( implicit tx: S#Tx ) {
      targets.dispose()
      disposeData()
   }
}

//object Mutating {
//   object Targets {
//      def apply[ S <: Sys[ S ]]( invalid: Boolean )( implicit tx: S#Tx ) : Targets[ S ] = {
//         val id         = tx.newID()
//         val children   = tx.newVar[ Children[ S ]]( id, IIdxSeq.empty )
//         val invalidVar = tx.newBooleanVar( id, invalid )
//         new Impl( id, children, invalidVar )
//      }
//
//      private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
//         val id         = tx.readID( in, access )
//         tx.readVal( id )( new ExpanderReader[ S ])
//      }
//
//      private class ExpanderReader[ S <: Sys[ S ]] extends TxnReader[ S#Tx, S#Acc, Reactor[ S ]] {
//         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
//            val targets    = Targets.read( in, access )
//            val observers  = targets.childrenVar.get.flatMap( _._2.toObserverKey )
//            tx.reactionMap.mapEventTargets( in, access, targets, observers )
//         }
//      }
//
//      private[event] def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
//         val cookie = in.readUnsignedByte()
//         require( cookie == 1, "Unexpected cookie " + cookie )
//         readIdentified( in, access )
//      }
//
//      private[event] def readIdentified[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
//         val id            = tx.readID( in, access )
//         val children      = tx.readVar[ Children[ S ]]( id, in )
//         val invalid       = tx.readBooleanVar( id, in )
//         new Impl[ S ]( id, children, invalid )
//      }
//
//      private[event] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ Children[ S ]],
//                                                invalid: S#Var[ Boolean ]) : Targets[ S ] =
//         new Impl( id, children, invalid )
//
//      private final class Impl[ S <: Sys[ S ]](
//         val id: S#ID, protected val childrenVar: S#Var[ Children[ S ]], invalid: S#Var[ Boolean ])
//      extends Targets[ S ] {
//         def isInvalid( implicit tx: S#Tx ) : Boolean = invalid.get
//         def validated()( implicit tx: S#Tx ) { invalid.set( false )}
//         def invalidate()( implicit tx: S#Tx ) { invalid.set( true )}
//
//         def write( out: DataOutput ) {
//            out.writeUnsignedByte( 1 )
//            id.write( out )
//            childrenVar.write( out )
//            invalid.write( out )
//         }
//
//         def dispose()( implicit tx: S#Tx ) {
//            require( !isConnected, "Disposing a event reactor which is still being observed" )
//            id.dispose()
//            childrenVar.dispose()
//            invalid.dispose()
//         }
//
//         def select( key: Int ) : ReactorSelector[ S ] = Selector( key, this )
//      }
//   }
//
//   sealed trait Targets[ S <: Sys[ S ]] extends event.Targets[ S ] {
//      /* private[event] */ def isInvalid( implicit tx: S#Tx ) : Boolean
//      def invalidate()( implicit tx: S#Tx ) : Unit
//      def validated()( implicit tx: S#Tx ) : Unit
//   }
//
//   trait Reader[ S <: Sys[ S ], +Repr ] extends event.Reader[ S, Repr, Targets[ S ]]
//
//   /**
//    * A trait to serialize events which are mutable nodes.
//    * An implementation mixing in this trait just needs to implement
//    * `read` with the `Event.Mutating.Targets` argument to return the node instance.
//    */
//   trait Serializer[ S <: Sys[ S ], Repr <: Mutating[ S, _ ]]
//   extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
//      final def write( v: Repr, out: DataOutput ) { v.write( out )}
//
//      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
//         val targets = Targets.read[ S ]( in, access )
//         read( in, access, targets /*, invalid */)
//      }
//   }
//}

///**
// * An event node `Mutating` internal state as part of the event propagation. Examples of this behavior
// * are caching algorithms or persisted data structures which need to adapt according to changes in
// * source events (e.g. a sorted collection storing mutable objects).
// *
// * This is implementation is INCOMPLETE at the moment. The idea is to enhance the event's `Targets`
// * with an invalidation flag which is set during propagation when no live reactions are hanging of the
// * node's target tree (in which case the targets are not fully deserialized to the `Mutating` node,
// * and thus the node is not able to update its internal state). When a mutating node is deserialized
// * it must check the targets' invalidation status and rebuild the internal state if necessary.
// */
//trait Mutating[ S <: Sys[ S ], A ] extends Node[ S, A ] {
//   protected def targets: Mutating.Targets[ S ]
//
//   final def select( key: Int ) : NodeSelector[ S ] = Selector( key, this )
//
//   final private[event] def addTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
//      targets.add( outlet, sel )
//   }
//
//   final private[event] def removeTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
//      targets.remove( outlet, sel )
//   }
//
//   final def dispose()( implicit tx: S#Tx ) {
//      targets.dispose()
//      disconnectNode()
//      disposeData()
//   }
//}

/**
 * The `Reactor` trait encompasses the possible targets (dependents) of an event. It defines
 * the `propagate` method which is used in the push-phase (first phase) of propagation. A `Reactor` is
 * either a persisted event `Node` or a registered `ObserverKey` which is resolved through the transaction
 * as pointing to a live view.
 */
sealed trait Reactor[ S <: Sys[ S ]] extends /* Reactor[ S ] */ Writer with Disposable[ S#Tx ] {
   def id: S#ID

   private[event] def select( inlet: Int ) : ReactorSelector[ S ]
   private[event] def children( implicit tx: S#Tx ) : Children[ S ]

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ Reactor[ _ ]]) {
         id == that.asInstanceOf[ Reactor[ _ ]].id
      } else super.equals( that ))
   }

   override def hashCode = id.hashCode()
}

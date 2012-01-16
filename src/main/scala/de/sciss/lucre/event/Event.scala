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

import collection.mutable.{Map => MMap}
import collection.immutable.{IndexedSeq => IIdxSeq}
import annotation.switch
import stm.{Writer, Sys, Disposable, TxnSerializer}

object Selector {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] = new Ser[ S ]

   def apply[ S <: Sys[ S ]]( key: Int, observer: ObserverKey[ S ]) : Selector[ S ] =
      new ObserverSelector[ S ]( key, observer )

   def apply[ S <: Sys[ S ]]( key: Int, targets: Invariant.Targets[ S ]) : Selector[ S ] =
      new InvariantSelector[ S ]( key, targets )

   def apply[ S <: Sys[ S ]]( key: Int, node: Invariant[ S, _ ]) : Selector[ S ] =
      new InvariantSelector[ S ]( key, node )

   def apply[ S <: Sys[ S ]]( key: Int, targets: Mutating.Targets[ S ]) : Selector[ S ] =
      new MutatingSelector[ S ]( key, targets )

   def apply[ S <: Sys[ S ]]( key: Int, node: Mutating[ S, _ ]) : Selector[ S ] =
      new MutatingSelector[ S ]( key, node )

   private sealed trait Impl[ S <: Sys[ S ]] extends Selector[ S ] {
      protected def reactor: Reactor[ S ]
      protected def cookie: Int

      final def write( out: DataOutput ) {
         out.writeInt( inlet )
         out.writeUnsignedByte( cookie )
         reactor.write( out )
      }

      override def toString = reactor.toString + ".select(" + inlet + ")"
   }

   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] {
      def write( v: Selector[ S ], out: DataOutput ) {
         v.write( out )
      }

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Selector[ S ] = {
         val selector = in.readInt()
         // 0 = invariant, 1 = mutating, 2 = observer
         val reactor = (in.readUnsignedByte(): @switch) match {
            case 0 =>
               Invariant.Targets.readAndExpand[ S ]( in, access )
            case 1 =>
               Mutating.Targets.readAndExpand[ S ]( in, access )
            case 2 =>
               val id = in.readInt()
               new ObserverKey[ S ]( id )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
         reactor.select( selector )
      }
   }

   private sealed trait NodeSelector[ S <: Sys[ S ]] extends Impl[ S ] {
      protected def reactor: NodeReactor[ S ]

      final private[event] def observerKey : Option[ ObserverKey[ S ]] = None

      final private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
                                       visited: Visited[ S ], reactions: Reactions )( implicit tx: S#Tx ) = {
         val cid     = reactor.id
         val bitset  = visited.getOrElse( cid, 0 )
         if( (bitset & inlet) == 0 ) {
            visited.+=( (cid, bitset | inlet) )
            reactor.propagate( source, update, parent, inlet, visited, reactions )
         } else reactions
      }
   }

   private final case class InvariantSelector[ S <: Sys[ S ]]( inlet: Int, reactor: NodeReactor[ S ])
   extends NodeSelector[ S ] {
      protected def cookie: Int = 0
   }

   private final case class MutatingSelector[ S <: Sys[ S ]]( inlet: Int, reactor: NodeReactor[ S ])
   extends NodeSelector[ S ] {
      protected def cookie: Int = 1
   }

   private final case class ObserverSelector[ S <: Sys[ S ]]( inlet: Int, reactor: ObserverKey[ S ])
   extends Impl[ S ] {
      private[event] def observerKey : Option[ ObserverKey[ S ]] = Some( reactor )

      private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
                                    visited: Visited[ S ], reactions: Reactions )( implicit tx: S#Tx ) =
         reactor.propagate( source, update, parent, outlet /* ! inlet */, visited, reactions ) // XXX TODO: do we need to deal with the visited map?

      protected def cookie: Int = 2
   }
}

sealed trait Selector[ S <: Sys[ S ]] extends Writer {
   def inlet: Int

   /**
    * @param   outlet   the outlet id of the event that propagates to this selector
    */
   private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
                                 visited: Visited[ S ], reactions: Reactions )
                               ( implicit tx: S#Tx ) : Reactions
   private[event] def observerKey : Option[ ObserverKey[ S ]] // Option[ Int ]
}

/**
 * An abstract trait uniting invariant and mutating readers.
 */
sealed trait Reader[ S <: Sys[ S ], +Repr, T ] {
   def read( in: DataInput, access: S#Acc, targets: T )( implicit tx: S#Tx ) : Repr
}

/**
 * A trait to serialize events which can be both constants and immutable nodes.
 * An implementation mixing in this trait just needs to implement methods
 * `readConstant` to return the constant instance, and `read` with the
 * `Event.Invariant.Targets` argument to return the immutable node instance.
 */
trait Serializer[ S <: Sys[ S ], Repr <: Writer /* Node[ S, _ ] */]
extends Invariant.Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
   final def write( v: Repr, out: DataOutput ) { v.write( out )}

   def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
      (in.readUnsignedByte(): @switch) match {
         case 3 => readConstant( in )
         case 0 =>
            val targets = Invariant.Targets.readIdentified[ S ]( in, access )
            read( in, access, targets )
         case cookie => sys.error( "Unexpected cookie " + cookie )
      }
   }

   def readConstant( in: DataInput )( implicit tx: S#Tx ) : Repr
}

object Observer {
   def apply[ S <: Sys[ S ], A, Repr ](
      reader: Reader[ S, Repr, _ ], fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] = {

      val key = tx.addEventReaction[ A, Repr ]( reader, fun )
      new Impl[ S, A, Repr ]( key )
   }

   private final class Impl[ S <: Sys[ S ], A, Repr ](
      key: ObserverKey[ S ])
   extends Observer[ S, A, Repr ] {
      override def toString = "Event.Observer<" + key.id + ">"

      def add( event: Event[ S, A, Repr ])( implicit tx: S#Tx ) {
//         event += key
         sys.error( "TODO" )
      }

      def remove( event: Event[ S, A, Repr ])( implicit tx: S#Tx ) {
//         event -= key
         sys.error( "TODO" )
      }

      def dispose()( implicit tx: S#Tx ) {
         tx.removeEventReaction( key )
      }
   }

   def dummy[ S <: Sys[ S ], A, Repr ] : Observer[ S, A, Repr ] = new Dummy[ S, A, Repr ]

   private final class Dummy[ S <: Sys[ S ], A, Repr ] extends Observer[ S, A, Repr ] {
      def add( event: Event[ S, A, Repr ])( implicit tx: S#Tx ) {}
      def remove( event: Event[ S, A, Repr ])( implicit tx: S#Tx ) {}
      def dispose()( implicit tx: S#Tx ) {}
   }
}

/**
 * `Observer` instances are returned by the `observe` method of classes implementing
 * `Observable`. The observe can be registered and unregistered with events.
 */
sealed trait Observer[ S <: Sys[ S ], A, Repr ] extends Disposable[ S#Tx ] {
   def add(    event: Event[ S, A, Repr ])( implicit tx: S#Tx ) : Unit
   def remove( event: Event[ S, A, Repr ])( implicit tx: S#Tx ) : Unit
}

/**
 * An abstract trait unifying invariant and mutating targets. This object is responsible
 * for keeping track of the dependents of an event source which is defined as the outer
 * object, sharing the same `id` as its targets. As a `Reactor`, it has a method to
 * `propagate` a fired event.
 */
sealed trait Targets[ S <: Sys[ S ]] extends NodeReactor[ S ] {
//      private[event] def id: S#ID

   protected def children: S#Var[ Children[ S ]]

   override def toString = "Event.Targets" + id

   /**
    * @param   outlet   the key of the event or selector that invoked this target's node's `propagate`
    */
   final private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
                                       visited: Visited[ S ], reactions: Reactions )( implicit tx: S#Tx ) : Reactions = {
      children.get.foldLeft( reactions ) { (rs, tup) =>
         val outlet2 = tup._1
         if( outlet2 == outlet ) {
            val sel = tup._2
            sel.propagate( source, update, parent, outlet, visited, rs )
         } else reactions
      }
   }

   final private[event] def addReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) : Boolean = {
      val tup  = (outlet, sel)
      val old  = children.get
      children.set( old :+ tup )
      old.isEmpty
   }

   final private[event] def removeReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) : Boolean = {
      val tup  = (outlet, sel)
      val xs   = children.get
      val i    = xs.indexOf( tup )
      if( i >= 0 ) {
         val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
         children.set( xs1 )
         xs1.isEmpty
      } else false
   }

   final def isConnected( implicit tx: S#Tx ) : Boolean = children.get.nonEmpty
}

//private final class TriggerImpl[ S <: Sys[ S ], A, A1 <: A, Repr <: Writer ]( protected val node: Node[ S, A ], key: Key[ A1, Repr ])
//extends Trigger.Impl[ S, A, A1, Repr ] with Root[ S, A1 /*, Repr */ ] {
//   override def toString = node.toString + "." + key.name
//
//   protected def selector: Int = key.id
//   protected def reader: Reader[ S, Repr, _ ] = key.keys.reader
//
////      def apply( update: A1 )( implicit tx: S#Tx ) {
////         val visited: Visited[ S ] = MMap.empty
////         val n          = node
////         val reactions  = n.propagate( this, update, n, key.id, visited, IIdxSeq.empty )
////         reactions.map( _.apply() ).foreach( _.apply() )
////      }
////
////      def +=( r: Event.Reactor[ S ])( implicit tx: S#Tx ) {
////         node.addReactor( r.select( key.id ))
////      }
////      def -=( r: Event.Reactor[ S ])( implicit tx: S#Tx ) {
////         node.removeReactor( r.select( key.id ))
////      }
//}

//sealed trait Key[ A, Repr <: Writer ] {
//   def name: String
//   private[event] def id: Int
//   private[event] def keys: Keys[ Repr ]
//   def unapply( id: Int ) : Boolean
//}
//
//trait Keys[ Repr <: Writer ] {
//   private var cnt = 0
//
////      implicit def reader[ S <: Sys[ S ]]: TxnReader[ S#Tx, S#Acc, Repr ]
//   implicit def reader[ S <: Sys[ S ]]: Reader[ S, Repr, _ ]
//
//   final protected def key[ A ] : Key[ A, Repr ] = key[ A ]( "(trigger)" )
//
//   final protected def key[ A ]( name: String ) : Key[ A, Repr ] = {
//      require( cnt < 31, "Key overflow" )
//      val id = 1 << cnt
//      cnt += 1
//      new KeyImpl[ A, Repr ]( id, name, this )
//   }
//
//   private final class KeyImpl[ A, Repr <: Writer ]( private[event] val id: Int, val name: String,
//                                           private[event] val keys: Keys[ Repr ])
//   extends Key[ A, Repr ] {
//      def unapply( i: Int ) : Boolean = i == id
//
//      override def toString = "Key[" + name + "]@" + id
//   }
//}

//   sealed trait Yield[ S <: Sys[ S ], A ] {
//      def pull( )( implicit tx: S#Tx ) : Option[ A ]
//   }

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
sealed trait Node[ S <: Sys[ S ], A ] extends NodeReactor[ S ] /* with Dispatcher[ S, A ] */ {
   protected def targets: Targets[ S ]
   protected def writeData( out: DataOutput ) : Unit
   protected def disposeData()( implicit tx: S#Tx ) : Unit

   private[event] def addReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) : Unit
   private[event] def removeReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) : Unit

   private[lucre] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ]

   final def id: S#ID = targets.id

//   final protected def event[ A1 <: A, Repr <: Node[ S, A ]]( key: Key[ A1, Repr ]) /* ( implicit ev: this.type <:< Repr ) */ : Trigger[ S, A1, Repr ] = {
//      new TriggerImpl[ S, A, A1, Repr ]( this, key )
//   }

   /**
    * @param   key   the key of the event or selector that invoked this method
    */
   private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], key: Int,
                                    visited: Visited[ S ], reactions: Reactions )
                                  ( implicit tx: S#Tx ) : Reactions =
      targets.propagate( source, update, this, key, visited, reactions ) // replace parent event node

   final def write( out: DataOutput ) {
      targets.write( out )
      writeData( out )
   }

   final def dispose()( implicit tx: S#Tx ) {
      targets.dispose()
      disposeData()
   }

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ Node[ _, _ ]]) {
         id == that.asInstanceOf[ Node[ _, _ ]].id
      } else super.equals( that ))
   }

   override def hashCode = id.hashCode()
}

object Invariant {
   object Targets {
      def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
         val id         = tx.newID()
         val children   = tx.newVar[ Children[ S ]]( id, IIdxSeq.empty )
         new Impl( id, children )
      }

      private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
         val targets    = read( in, access )
         val observers  = targets.children.get.flatMap( _._2.observerKey )
         tx.mapEventTargets( in, access, targets, observers )
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
         val id: S#ID, protected val children: S#Var[ Children[ S ]])
      extends Targets[ S ] {
         def write( out: DataOutput ) {
            out.writeUnsignedByte( 0 )
            id.write( out )
            children.write( out )
         }

         def dispose()( implicit tx: S#Tx ) {
            require( !isConnected, "Disposing a event reactor which is still being observed" )
            id.dispose()
            children.dispose()
         }

         def select( key: Int ) : Selector[ S ] = Selector( key, this )
      }
   }

   sealed trait Targets[ S <: Sys[ S ]] extends event.Targets[ S ] {
//         final def select( key: Int ) : Selector[ S ] = Selector( key, this )
   }

   trait Reader[ S <: Sys[ S ], +Repr ] extends event.Reader[ S, Repr, Targets[ S ]] {
//         def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Repr
   }

   /**
    * A trait to serialize events which are immutable nodes.
    * An implementation mixing in this trait just needs to implement
    * `read` with the `Event.Invariant.Targets` argument to return the node instance.
    */
   trait Serializer[ S <: Sys[ S ], Repr <: /* Writer */ Invariant[ S, _ ]]
   extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
      final def write( v: Repr, out: DataOutput ) { v.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
//         val cookie = in.readUnsignedByte()
//         if( cookie == 0 ) {
//            val targets = Targets.readIdentified[ S ]( in, access )
            val targets = Targets.read[ S ]( in, access )
            read( in, access, targets )
//         } else {
//            sys.error( "Unexpected cookie " + cookie )
//         }
      }
   }
}

/**
 * A late binding event node is one which only registers with its sources after the first
 * target (dependent) is registered. Vice versa, it automatically unregisters from its sources
 * after the last dependent is removed. Implementing classes must provide the `sources` method
 * which defines a fixed number of sources for the event.
 */
trait LateBinding[ S <: Sys[ S ], A ] extends Node[ S, A ] {
   protected def sources( implicit tx: S#Tx ) : Sources[ S ]

   final private[event] def addReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) {
      if( targets.addReactor( outlet, sel )) {
//            sources.foreach( _.addReactor( this ))
         sources.foreach( tup => tup._1 += this.select( tup._2 ))
      }
   }

   final private[event] def removeReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) {
      if( targets.removeReactor( outlet, sel )) {
//            sources.foreach( _.removeReactor( this ))
         sources.foreach( tup => tup._1 -= this.select( tup._2 ))
      }
   }
}

/**
 * An early binding event node simply
 */
trait EarlyBinding[ S <: Sys[ S ], A ] extends Node[ S, A ] {
   final private[event] def addReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) {
      targets.addReactor( outlet, sel )
   }

   final private[event] def removeReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) {
      targets.removeReactor( outlet, sel )
   }

   protected def addSource( r: Event[ S, _, _ ], inlet: Int )( implicit tx: S#Tx ) {
      r += this.select( inlet )
   }

   protected def removeSource( r: Event[ S, _, _ ], inlet: Int )( implicit tx: S#Tx ) {
      r += this.select( inlet )
   }
}

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
   protected def targets: Invariant.Targets[ S ]

   final def select( key: Int ) : Selector[ S ] = Selector( key, this )

   override def toString = "Event.Invariant" + id
}

/**
 * A rooted event does not have sources. This trait provides a simple
 * implementation of `pull` which merely checks if this event has fired or not.
 */
trait Root[ S <: Sys[ S ], A ] /* extends Node[ S, A, Repr ] */ {
   final private[lucre] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] =
      pull( source, update )

   final /* override */ private[lucre] def pull( /* key: Int, */ source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] = {
      if( source == this ) Some( update.asInstanceOf[ A ]) else None
   }
}

/**
 * Value based events fire instances of `Change` which provides the value before
 * and after modification.
 */
final case class Change[ @specialized A ]( before: A, now: A ) {
   def isSignificant: Boolean = before != now
   def toOption: Option[ Change[ A ]] = if( isSignificant ) Some( this ) else None
}

/**
 * A constant "event" is one which doesn't actually fire. It thus arguably isn't really an event,
 * but it can be used to implement the constant type of an expression system which can use a unified
 * event approach, where the `Constant` event just acts as a dummy event. `addReactor` and `removeReactor`
 * have no-op implementations. Also `pull` in inherited from `Root`, but will always return `None`
 * as there is no way to fire this event. Implementation must provide a constant value method
 * `constValue` and implement its serialization via `writeData`.
 */
trait Constant[ S <: Sys[ S ] /*, A */] /* extends Val[ S, A ] with Root[ S, Change[ A ]] */ {
//      protected def constValue : A
//      final def value( implicit tx: S#Tx ) : A = constValue
   final private[event] def addReactor(     r: Reactor[ S ])( implicit tx: S#Tx ) {}
   final private[event] def removeReactor(  r: Reactor[ S ])( implicit tx: S#Tx ) {}

   final def write( out: DataOutput ) {
      out.writeUnsignedByte( 3 )
      writeData( out )
   }

   protected def writeData( out: DataOutput ) : Unit
}

/**
 * A `Singleton` event is one which doesn't carry any state. This is a utility trait
 * which provides no-op implementations for `writeData` and `disposeData`.
 */
trait Singleton[ S <: Sys[ S ]] {
   final protected def disposeData()( implicit tx: S#Tx ) {}
   final protected def writeData( out: DataOutput ) {}
}

trait Impl[ S <: Sys[ S ], A, A1 <: A, Repr ] extends Event[ S, A1, Repr ] {
   protected def outlet: Int
   protected def node: Node[ S, A ]

   protected def reader: Reader[ S, Repr, _ ]
//      implicit protected def serializer: TxnSerializer[ S#Tx, S#Acc, Event[ S, A1, Repr ]]

   final def +=( r: Selector[ S ])( implicit tx: S#Tx ) {
//      node.addReactor( r.select( selector ))
      node.addReactor( outlet, r )
   }

   final def -=( r: Selector[ S ])( implicit tx: S#Tx ) {
      node.removeReactor( outlet, r )
   }

   final def react( fun: (S#Tx, A1) => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] = {
      val res = Observer[ S, A1, Repr ]( reader, fun )
      res.add( this )
      res
   }

//   private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A1 ] = {
//      // no way to make the node's pull be generic in the result type without big big fuss
//      // (http://stackoverflow.com/questions/8798035/possible-to-perform-pattern-match-on-a-generic-value-with-type-conforming-result/8803684#8803684)
////      node.pull( selector, source, update ).collect {
////         case value: A1 => value
////      }
//      node.pull( selector, source, update ).asInstanceOf[ Option[ A1 ]]  // :-(
//   }

//      final def filter[ P <: (A) => Boolean ]( pred: P )( implicit tx: S#Tx ) : Event.Flat[ S, A1 ] =
//         Filter[ S, A1, Event[ S, A1, Repr ], P ]( this )( pred )
}

/**
 * Standalone events unite a node and one particular event.
 */
trait StandaloneLike[ S <: Sys[ S ], A, Repr ] extends Impl[ S, A, A, Repr ] with Invariant[ S, A ]
/* with EarlyBinding[ S, A ] */ /* with Singleton[ S ] with Root[ S, A ] */ {
   final protected def outlet = 1
   final protected def node: Node[ S, A ] = this

//   final def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] =
//      pull( source, update )
}

trait Source[ S <: Sys[ S ], A, A1 <: A, Repr ] extends Event[ S, A1, Repr ] {
   protected def outlet: Int
   protected def node: Node[ S, A ]

   final protected def fire( update: A1 )( implicit tx: S#Tx ) {
      val visited: Visited[ S ] = MMap.empty
      val n          = node
      val reactions  = n.propagate( this, update, n, outlet, visited, IIdxSeq.empty )
      reactions.map( _.apply() ).foreach( _.apply() )
   }
}

object Trigger {
   trait Impl[ S <: Sys[ S ], A, A1 <: A, Repr ] extends Trigger[ S, A1, Repr ] with event.Impl[ S, A, A1, Repr ]
   with Source[ S, A, A1, Repr ] {
      final def apply( update: A1 )( implicit tx: S#Tx ) { fire( update )}
   }

   def apply[ S <: Sys[ S ], A ]( implicit tx: S#Tx ) : Standalone[ S, A ] = new Standalone[ S, A ] {
      protected val targets = Invariant.Targets[ S ]
   }

   object Standalone {
      implicit def serializer[ S <: Sys[ S ], A ] : Invariant.Serializer[ S, Standalone[ S, A ]] =
         new Invariant.Serializer[ S, Standalone[ S, A ]] {
            def read( in: DataInput, access: S#Acc, _targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Standalone[ S, A ] =
               new Standalone[ S, A ] {
                  protected val targets = _targets
               }
         }
   }
   trait Standalone[ S <: Sys[ S ], A ] extends Impl[ S, A, A, Standalone[ S, A ]]
   with StandaloneLike[ S, A, Standalone[ S, A ]] with Singleton[ S ] with EarlyBinding[ S, A ]
   with Root[ S, A /*, Standalone[ S, A ] */ ] {
      final protected def reader: Reader[ S, Standalone[ S, A ], _ ] = Standalone.serializer[ S, A ]
   }
}

/**
 * A `Trigger` event is one which can be publically fired. One can think of it as the
 * imperative event in EScala.
 */
trait Trigger[ S <: Sys[ S ], A, Repr ] extends Event[ S, A, Repr ] {
   def apply( update: A )( implicit tx: S#Tx ) : Unit
}

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
trait Bang[ S <: Sys[ S ]] extends Trigger.Impl[ S, Unit, Unit, Bang[ S ]] with StandaloneLike[ S, Unit, Bang[ S ]]
with EarlyBinding[ S, Unit ] {
   /**
    * A parameterless convenience version of the `Trigger`'s `apply` method.
    */
   def apply()( implicit tx: S#Tx ) { apply( () )}

   override def toString = "Bang"
}

object Mutating {
   object Targets {
      def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
         val id         = tx.newID()
         val children   = tx.newVar[ Children[ S ]]( id, IIdxSeq.empty )
         val invalid    = tx.newBooleanVar( id, false )
         new Impl( id, children, invalid )
      }

      private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
         val targets    = read( in, access )
         val observers  = targets.children.get.flatMap( _._2.observerKey )
         tx.mapEventTargets( in, access, targets, observers )
      }

      private[event] def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
         val cookie = in.readUnsignedByte()
         require( cookie == 1, "Unexpected cookie " + cookie )
         readIdentified( in, access )
      }

      private[event] def readIdentified[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
         val id            = tx.readID( in, access )
         val children      = tx.readVar[ Children[ S ]]( id, in )
         val invalid       = tx.readBooleanVar( id, in )
         new Impl[ S ]( id, children, invalid )
      }

      private[event] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ Children[ S ]],
                                                invalid: S#Var[ Boolean ]) : Targets[ S ] =
         new Impl( id, children, invalid )

      private final class Impl[ S <: Sys[ S ]](
         val id: S#ID, protected val children: S#Var[ Children[ S ]], invalid: S#Var[ Boolean ])
      extends Targets[ S ] {
         def isInvalid( implicit tx: S#Tx ) : Boolean = invalid.get
         def validated()( implicit tx: S#Tx ) { invalid.set( false )}

         def write( out: DataOutput ) {
            out.writeUnsignedByte( 1 )
            id.write( out )
            children.write( out )
            invalid.write( out )
         }

         def dispose()( implicit tx: S#Tx ) {
            require( !isConnected, "Disposing a event reactor which is still being observed" )
            id.dispose()
            children.dispose()
            invalid.dispose()
         }

         def select( key: Int ) : Selector[ S ] = Selector( key, this )
      }
   }

   sealed trait Targets[ S <: Sys[ S ]] extends event.Targets[ S ] {
      private[event] def isInvalid( implicit tx: S#Tx ) : Boolean
//         final def select( key: Int ) : Selector[ S ] = Selector( key, this )
      def validated()( implicit tx: S#Tx ) : Unit
   }

   trait Reader[ S <: Sys[ S ], +Repr ] extends event.Reader[ S, Repr, Targets[ S ]] {
//         def read( in: DataInput, access: S#Acc, targets: Targets[ S ] /*, revalidate: Boolean */)( implicit tx: S#Tx ) : Repr
   }

   /**
    * A trait to serialize events which are mutable nodes.
    * An implementation mixing in this trait just needs to implement
    * `read` with the `Event.Mutating.Targets` argument to return the node instance.
    */
   trait Serializer[ S <: Sys[ S ], Repr <: Mutating[ S, _ ]]
   extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
      final def write( v: Repr, out: DataOutput ) { v.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
//         val cookie = in.readUnsignedByte()
//         if( cookie == 1 ) {
            val targets = Targets.read[ S ]( in, access )
            val invalid = targets.isInvalid
            val res     = read( in, access, targets /*, invalid */)
            if( invalid ) require( !targets.isInvalid, "Reader did not validate structure" )
            res
//         } else {
//            sys.error( "Unexpected cookie " + cookie )
//         }
      }
   }
}

/**
 * An event node `Mutating` internal state as part of the event propagation. Examples of this behavior
 * are caching algorithms or persisted data structures which need to adapt according to changes in
 * source events (e.g. a sorted collection storing mutable objects).
 *
 * This is implementation is INCOMPLETE at the moment. The idea is to enhance the event's `Targets`
 * with an invalidation flag which is set during propagation when no live reactions are hanging of the
 * node's target tree (in which case the targets are not fully deserialized to the `Mutating` node,
 * and thus the node is not able to update its internal state). When a mutating node is deserialized
 * it must check the targets' invalidation status and rebuild the internal state if necessary.
 */
trait Mutating[ S <: Sys[ S ], A ] extends Node[ S, A ] {
   protected def targets: Mutating.Targets[ S ]

   final def select( key: Int ) : Selector[ S ] = Selector( key, this )

   override def toString = "Event.Mutating" + id
}

/**
 * The sealed `Reactor` trait encompasses the possible targets (dependents) of an event. It defines
 * the `propagate` method which is used in the push-phase (first phase) of propagation. A `Reactor` is
 * either a persisted event `Node` or a registered `ObserverKey` which is resolved through the transaction
 * as pointing to a live view.
 */
sealed trait Reactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
   def select( key: Int ) : Selector[ S ]
   private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], key: Int,
                                 visited: Visited[ S ], reactions: Reactions )( implicit tx: S#Tx ) : Reactions
}

sealed trait NodeReactor[ S <: Sys[ S ]] extends Reactor[ S ] {
   def id: S#ID
}

object Dummy {
   def apply[ S <: Sys[ S ], A, Repr ] : Dummy[ S, A, Repr ] = new Dummy[ S, A, Repr ] {}
}
trait Dummy[ S <: Sys[ S ], A, Repr ] extends Event[ S, A, Repr ] {
   final def +=( r: Selector[ S ])( implicit tx: S#Tx ) {}
   final def -=( r: Selector[ S ])( implicit tx: S#Tx ) {}

   final def react( fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] =
      Observer.dummy[ S, A, Repr ]

   final private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] = None
}

/**
 * Instances of `ObserverKey` are provided by methods in `Txn`, when a live `Observer` is registered. Since
 * the observing function is not persisted, the key will be used for lookup (again through the transaction)
 * of the reacting function during the first reaction gathering phase of event propagation.
 */
final case class ObserverKey[ S <: Sys[ S ]] private[lucre] ( id: Int ) extends Reactor[ S ] {
   private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
                                 visited: Visited[ S ], reactions: Reactions )( implicit tx: S#Tx ) : Reactions = {
      tx.propagateEvent( this, source, update, parent, outlet, /* visited, */ reactions )
   }

   def select( key: Int ) : Selector[ S ] = Selector( key, this )

   def dispose()( implicit tx: S#Tx ) {}  // XXX really?

   def write( out: DataOutput ) {
//         out.writeUnsignedByte( 2 )
      out.writeInt( id )
   }
}

/**
 * `Event` is not sealed in order to allow you define traits inheriting from it, while the concrete
 * implementations should extend either of `Event.Constant` or `Event.Node` (which itself is sealed and
 * split into `Event.Invariant` and `Event.Mutating`.
 */
trait Event[ S <: Sys[ S ], A, Repr ] /* extends Writer */ {
//   def +=( r: Reactor[ S ])( implicit tx: S#Tx ) : Unit
//   def -=( r: Reactor[ S ])( implicit tx: S#Tx ) : Unit
   def +=( r: Selector[ S ])( implicit tx: S#Tx ) : Unit
   def -=( r: Selector[ S ])( implicit tx: S#Tx ) : Unit

   def react( fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ]

   private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ]
}


object Compound {
   final protected class EventOps[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B ]( d: Compound[ S, Repr, D ],
                                                                e: Event[ S, B, _ ]) {
      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, fun, d.decl.eventID[ A1 ])
      def |[ Up >: B, C <: Up ]( that: Event[ S, C, _ ]) : EventOr[ S, Repr, D, Up ] =
         new EventOr[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, _ <: Up, _ ]]( e, that ))
   }

   final protected class CollectionOps[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], Elem, B ](
      d: Compound[ S, Repr, D ], elem: Elem => Event[ S, B, _ ]) {

      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : CollectionEvent[ S, Repr, D, Elem, B, A1 ] =
         new CollectionEvent[ S, Repr, D, Elem, B, A1 ]( d, elem, fun, d.decl.eventID[ A1 ])
   }

   final class CollectionEvent[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], Elem, B, A1 <: D#Update ] private[Compound](
      protected val node: Compound[ S, Repr, D ], elemEvt: Elem => Event[ S, B, _ ], fun: B => A1,
      protected val outlet: Int )
   extends event.Impl[ S, D#Update, A1, Repr ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]

      def +=( elem: Elem )( implicit tx: S#Tx ) {
//         elemEvt( elem ) += this
         sys.error( "TODO" )
      }

      def -=( elem: Elem )( implicit tx: S#Tx ) {
//         elemEvt( elem ) -= this
         sys.error( "TODO" )
      }

      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A1 ] = {
         sys.error( "TODO" )
      }
   }

   final class EventOr[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B ] private[Compound](
      d: Compound[ S, Repr, D ], elems: IIdxSeq[ Event[ S, _ <: B, _ ]]) {
      def |[ Up >: B, C <: Up ]( that: Event[ S, C, _ ]) : EventOr[ S, Repr, D, Up ] =
         new EventOr[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, _ <: Up, _ ]]( elems: _* ) :+ that )

      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new OrMap[ S, Repr, D, B, A1 ]( d, elems, fun, d.decl.eventID[ A1 ])
   }

   private final class Map[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B, A1 <: D#Update ](
      protected val node: Compound[ S, Repr, D ], e: Event[ S, B, _ ], fun: B => A1,
      protected val outlet: Int )
   extends event.Impl[ S, D#Update, A1, Repr ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]

      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A1 ] = {
         e.pull( source, update ).map( fun )
      }
   }

   private final class OrMap[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B, A1 <: D#Update ](
      protected val node: Compound[ S, Repr, D ], events: IIdxSeq[ Event[ S, _ <: B, _ ]], fun: B => A1,
      protected val outlet: Int )
   extends event.Impl[ S, D#Update, A1, Repr ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]

      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A1 ] = {
         events.view.flatMap( _.pull( source, update )).headOption.map( fun )
      }
   }

   private final class Trigger[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], A1 <: D#Update ](
      protected val node: Compound[ S, Repr, D ], protected val outlet: Int )
   extends event.Trigger.Impl[ S, D#Update, A1, Repr ] with Root[ S, A1 ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]
   }
}
trait Compound[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ]] extends Node[ S, D#Update ] {
   me: Repr =>

   import de.sciss.lucre.{event => evt}

   protected def decl: D // Decl[ Repr ]

   implicit protected def eventOps[ B ]( e: Event[ S, B, _ ]) : Compound.EventOps[ S, Repr, D, B ] =
      new Compound.EventOps( this, e )

   protected def event[ A1 <: D#Update ]( implicit m: ClassManifest[ A1 ]) : evt.Trigger[ S, A1, Repr ] =
      new Compound.Trigger( this, decl.eventID[ A1 ])

   protected def collection[ Elem, B ]( fun: Elem => Event[ S, B, _ ]) : Compound.CollectionOps[ S, Repr, D, Elem, B ] =
      new Compound.CollectionOps[ S, Repr, D, Elem, B ]( this, fun )

   final private[lucre] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ D#Update ] = {
      decl.pull( this, key, source, update ) // .asInstanceOf[ Option[ D#Update ]]
   }
}

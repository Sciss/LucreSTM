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

import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}
import collection.mutable.{Buffer, Map => MMap}
import annotation.switch
import scala.util.MurmurHash
import stm.{TxnReader, Writer, Sys, Disposable, TxnSerializer}

object Selector {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] = new Ser[ S ]
//   implicit def reader[ S <: Sys[ S ]] : TxnReader[ S#Tx, S#Acc, Selector[ S ]] = new Reader[ S ]

   implicit def event[ S <: Sys[ S ]]( ev: Event[ S, _, _ ]) : ReactorSelector[ S ] with ExpandedSelector[ S ] = ev.select()

//   def apply[ S <: Sys[ S ]]( key: Int, observer: ObserverKey[ S ]) : Selector[ S ] =
//      new ObserverSelector[ S ]( key, observer )

   def apply[ S <: Sys[ S ]]( key: Int, targets: Invariant.Targets[ S ]) : ReactorSelector[ S ] =
      new InvariantTargetsSelector[ S ]( key, targets )

   def apply[ S <: Sys[ S ] /*, A */]( key: Int, node: Invariant[ S, _ /* A */]) : ReactorSelector[ S ] with ExpandedSelector[ S ] =
      new InvariantNodeSelector[ S /*, A, Invariant[ S, A ] */]( key, node )

   def apply[ S <: Sys[ S ]]( key: Int, targets: Mutating.Targets[ S ]) : ReactorSelector[ S ] =
      new MutatingTargetsSelector[ S ]( key, targets )

   def apply[ S <: Sys[ S ] /*, A */]( key: Int, node: Mutating[ S, _ /* A */]) : ReactorSelector[ S ] with ExpandedSelector[ S ] =
      new MutatingNodeSelector[ S /*, A, Mutating[ S, A ] */]( key, node )

//   private final class Reader[ S <: Sys[ S ]] extends TxnReader[ S#Tx, S#Acc, Selector[ S ]]
   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] {
      def write( v: Selector[ S ], out: DataOutput ) {
         v.write( out )
      }

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Selector[ S ] = {
         // 0 = invariant, 1 = mutating, 2 = observer
         (in.readUnsignedByte(): @switch) match {
            case 0 =>
               val inlet   = in.readInt()
               val targets = Invariant.Targets.readAndExpand[ S ]( in, access )
               targets.select( inlet )
//               Selector( inlet, targets )
            case 1 =>
               val inlet   = in.readInt()
               val targets = Mutating.Targets.readAndExpand[ S ]( in, access )
               targets.select( inlet )
//               Selector( inlet, targets )
            case 2 =>
               val id = in.readInt()
               new ObserverKey[ S ]( id )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
//         reactor.select( selector )
      }
   }

   private sealed trait InvariantSelector {
      protected def cookie: Int = 0
   }

   private sealed trait MutatingSelector {
      protected def cookie: Int = 1
   }

   private sealed trait TargetsSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
      final def nodeOption: Option[ NodeSelector[ S ]] = None

//      final private[event] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ Any ] =
//         sys.error( "Operation not supported" ) // EmptyPull

//      final private[event] def expand[ A, Repr <: Node[ S, A ]]( implicit reader: TxnReader[ S#Tx, S#Acc, Repr ]) : NodeSelector[ S, A, Repr ] = {
//         sys.error( "TODO" )
//      }
   }

   private final case class InvariantNodeSelector[ S <: Sys[ S ] /*, A, Repr <: Invariant[ S, A ] */]( inlet: Int, reactor: Invariant[ S, _ ] /* Repr */)
   extends NodeSelector[ S /*, A, Repr */ ] with InvariantSelector

   private final case class InvariantTargetsSelector[ S <: Sys[ S ]]( inlet: Int, reactor: Invariant.Targets[ S ])
   extends TargetsSelector[ S ] with InvariantSelector

   private final case class MutatingNodeSelector[ S <: Sys[ S ] /*, A, Repr <: Mutating[ S, A ] */]( inlet: Int, reactor: Mutating[ S, _ ] /* Repr */)
   extends NodeSelector[ S /*, A, Repr */] with MutatingSelector

   private final case class MutatingTargetsSelector[ S <: Sys[ S ]]( inlet: Int, reactor: Mutating.Targets[ S ])
   extends TargetsSelector[ S ] with MutatingSelector {
      override private[event] def pushUpdate( update: Any, parent: ReactorSelector[ S ], visited: Visited[ S ],
                                              reactions: Reactions )( implicit tx: S#Tx ) {
         reactor.invalidate()
         super.pushUpdate( update, parent, visited, reactions )
      }
   }
}

sealed trait Selector[ S <: Sys[ S ]] extends Writer {
   protected def cookie: Int

   final def write( out: DataOutput ) {
      out.writeUnsignedByte( cookie )
      writeData( out )
   }

   protected def writeData( out: DataOutput ) : Unit

//   private[event] def pull( path: Path[ S ], update: Any )( implicit tx: S#Tx ) : Option[ Any ]

   private[event] def pushUpdate( update: Any, parent: ReactorSelector[ S ], visited: Visited[ S ],
                                  reactions: Reactions )( implicit tx: S#Tx ) : Unit
   private[event] def toObserverKey : Option[ ObserverKey[ S ]] // Option[ Int ]
}

sealed trait ReactorSelector[ S <: Sys[ S ]] extends Selector[ S ] {
   def reactor: Reactor[ S ]
   def inlet: Int

//   private[event] def expand[ A, Repr <: Node[ S, A ]]( implicit reader: TxnReader[ S#Tx, S#Acc, Repr ]) : NodeSelector[ S, A, Repr ]
   def nodeOption: Option[ NodeSelector[ S ]]

   final protected def writeData( out: DataOutput ) {
      out.writeInt( inlet )
//      reactor.write( out )
      reactor.id.write( out )
   }

   override def hashCode : Int = {
      import MurmurHash._
      var h = startHash( 2 )
      val c = startMagicA
      val k = startMagicB
      h = extendHash( h, inlet, c, k )
//      h = extendHash( h, reactor.##, nextMagicA( c ), nextMagicB( k ))
      h = extendHash( h, reactor.id.##, nextMagicA( c ), nextMagicB( k ))
      finalizeHash( h )
   }

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ ReactorSelector[ _ ]]) {
         val thatSel = that.asInstanceOf[ ReactorSelector[ _ ]]
//         (inlet == thatSel.inlet && reactor == thatSel.reactor)
         (inlet == thatSel.inlet && reactor.id == thatSel.reactor.id)
      } else super.equals( that ))
   }

   final private[event] def toObserverKey : Option[ ObserverKey[ S ]] = None

   override def toString = reactor.toString + ".select(" + inlet + ")"

//   private[event] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ Any ]

//   final private[event] def pushUpdate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
//                                        path: Path[ S ], visited: Visited[ S ],
//                                        reactions: Reactions )( implicit tx: S#Tx ) {
//      val cid     = reactor.id
//      val bitset  = visited.getOrElse( cid, 0 )
//      if( (bitset & inlet) == 0 ) {
//         visited.+=( (cid, bitset | inlet) )
////         reactor.propagate( source, update, parent, inlet, path, visited, reactions )
//         val path1 = this :: path
//         reactor.children.foreach { tup =>
//            val inlet2 = tup._1
//            if( inlet2 == inlet ) {
//               val sel = tup._2
//               sel.pushUpdate( source, update, parent, outlet, path1, visited, reactions )
//            }
//         }
//      }
//   }

   /* final */ private[event] def pushUpdate( update: Any, parent: ReactorSelector[ S ], visited: Visited[ S ],
                                        reactions: Reactions )( implicit tx: S#Tx ) {
      val parents = visited.getOrElse( this, NoParents )
      visited += ((this, parents + parent))
      if( parents.isEmpty ) {
         reactor.children.foreach { tup =>
            val inlet2 = tup._1
            if( inlet2 == inlet ) {
               val sel = tup._2
               sel.pushUpdate( update, this, visited, reactions )
            }
         }
      }
   }
}

sealed trait ExpandedSelector[ S <: Sys[ S ]] extends Selector[ S ] /* with Writer */ {
//   protected def cookie: Int
//
//   final def write( out: DataOutput ) {
//      out.writeUnsignedByte( cookie )
//      writeData( out )
//   }
//
//   protected def writeData( out: DataOutput ) : Unit
}

sealed trait NodeSelector[ S <: Sys[ S ] /*, A, Repr <: Node[ S, A ] */] extends ReactorSelector[ S ] with ExpandedSelector[ S ] {
//   def reactor: Repr
   def reactor: Node[ S, _ ]

   final def nodeOption: Option[ NodeSelector[ S ]] = Some( this )

   final private[event] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ Any ] = {
      reactor.getEvent( inlet ).pullUpdate( visited, update )
   }

//   final private[event] def expand[ A1, Repr1 <: Node[ S, A1 ]]( implicit reader: TxnReader[ S#Tx, S#Acc, Repr1 ]) : NodeSelector[ S, A1, Repr1 ] = {
//      this.asInstanceOf[ NodeSelector[ S, A1, Repr1 ]]   // XXX not nice :-(
//   }
}

/**
 * Instances of `ObserverKey` are provided by methods in `Txn`, when a live `Observer` is registered. Since
 * the observing function is not persisted, the key will be used for lookup (again through the transaction)
 * of the reacting function during the first reaction gathering phase of event propagation.
 */
final case class ObserverKey[ S <: Sys[ S ]] private[lucre] ( id: Int ) extends ExpandedSelector[ S ] {
   protected def cookie: Int = 2

   private[event] def toObserverKey : Option[ ObserverKey[ S ]] = Some( this )

   private[event] def pushUpdate( update: Any, parent: ReactorSelector[ S ], visited: Visited[ S ],
                                  reactions: Reactions )( implicit tx: S#Tx ) {
      val nParent = parent.nodeOption.getOrElse( sys.error( "Orphan observer - no expanded node selector" ))
      tx.processEvent( this, update, nParent, visited, reactions )
   }

//   def select( key: Int ) : Selector[ S ] = Selector( key, this )

   def dispose()( implicit tx: S#Tx ) {}  // XXX really?

   protected def writeData( out: DataOutput ) {
      out.writeInt( id )
   }
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
      reader: Reader[ S, Repr, _ ], fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] = {

      val key = tx.addEventReaction[ A, Repr ]( reader, fun )
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
         tx.removeEventReaction( key )
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

/**
 * An abstract trait unifying invariant and mutating targets. This object is responsible
 * for keeping track of the dependents of an event source which is defined as the outer
 * object, sharing the same `id` as its targets. As a `Reactor`, it has a method to
 * `propagate` a fired event.
 */
sealed trait Targets[ S <: Sys[ S ]] extends Reactor[ S ] /* extends Writer with Disposable[ S#Tx ] */ {
   /* private[event] */ def id: S#ID

//   def select( key: Int ) : Selector[ S ]

   protected def childrenVar: S#Var[ Children[ S ]]

   override def toString = "Targets" + id

//   /**
//    * @param   outlet   the key of the event or selector that invoked this target's node's `propagate`
//    */
//   final private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], outlet: Int,
//                                       path: Path[ S ], visited: Visited[ S ],
//                                       reactions: Reactions )( implicit tx: S#Tx ) {
//      val path1 = id :: path
//      children.get.foreach { tup =>
//         val outlet2 = tup._1
//         if( outlet2 == outlet ) {
//            val sel = tup._2
//            sel.propagate( source, update, parent, outlet, path1, visited, reactions )
//         }
//      }
//   }

   final private[event] def children( implicit tx: S#Tx ) : Children[ S ] = childrenVar.get

   final private[event] def add( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean = {
      val tup  = (outlet, sel)
      val old  = childrenVar.get
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
sealed trait Node[ S <: Sys[ S ], A ] extends Reactor[ S ] /* with Dispatcher[ S, A ] */ {
   override def toString = "Node" + id

   protected def targets: Targets[ S ]
   protected def writeData( out: DataOutput ) : Unit
   protected def disposeData()( implicit tx: S#Tx ) : Unit

   final private[event] def children( implicit tx: S#Tx ) = targets.children

   private[event] def select( inlet: Int ) : ReactorSelector[ S ] with ExpandedSelector[ S ]

//   final protected def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq.empty
//   protected def events : IIdxSeq[ Event[ S, _, _ ]]
//   protected final def events : IIdxSeq[ Event[ S, _, _ ]] = IIdxSeq.empty

//   private[event] def addReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) : Unit
//   private[event] def removeReactor( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) : Unit

   protected def connectNode()(    implicit tx: S#Tx ) : Unit
   protected def disconnectNode()( implicit tx: S#Tx ) : Unit

   private[event] def addTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Unit
   private[event] def removeTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Unit

//   final private[event] def addTarget( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) {
//      if( targets.add( outlet, sel )) {
////         events.foreach( _.connectSources() )
//         connectNode()
//      }
//   }
//
//   final private[event] def removeTarget( outlet: Int, sel: Selector[ S ])( implicit tx: S#Tx ) {
//      if( targets.remove( outlet, sel )) {
////         events.foreach( _.disconnectSources() )
//         disconnectNode()
//      }
//   }

//   private[lucre] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ]
//   private[lucre] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ]

//   private[lucre] def pull( key: Int, path: Path[ S ], update: Any )( implicit tx: S#Tx ) : Option[ A ]
   private[event] def getEvent( key: Int ) : Event[ S, _ <: A, _ ]

   final def id: S#ID = targets.id

//   final protected def event[ A1 <: A, Repr <: Node[ S, A ]]( key: Key[ A1, Repr ]) /* ( implicit ev: this.type <:< Repr ) */ : Trigger[ S, A1, Repr ] = {
//      new TriggerImpl[ S, A, A1, Repr ]( this, key )
//   }

//   /**
//    * @param   key   the key of the event or selector that invoked this method
//    */
//   private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], key: Int,
//                                 path: Path[ S ], visited: Visited[ S ], reactions: Reactions )
//                               ( implicit tx: S#Tx ) {
//      targets.propagate( source, update, this, key, path, visited, reactions ) // replace parent event node
//   }

   final def write( out: DataOutput ) {
      targets.write( out )
      writeData( out )
   }

//   final def dispose()( implicit tx: S#Tx ) {
//      targets.dispose()
//      disposeData()
//   }

//   override def equals( that: Any ) : Boolean = {
//      (if( that.isInstanceOf[ Node[ _, _ ]]) {
//         id == that.asInstanceOf[ Node[ _, _ ]].id
//      } else super.equals( that ))
//   }
//
//   override def hashCode = id.hashCode()
}

object Invariant {
   object Targets {
      def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
         val id         = tx.newID()
         val children   = tx.newVar[ Children[ S ]]( id, NoChildren )
         new Impl( id, children )
      }

      private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
//         val targets    = read( in, access )
//         val observers  = targets.childrenVar.get.flatMap( _._2.toObserverKey )
//         tx.mapEventTargets( in, access, targets, observers )
         val id         = tx.readID( in, access )
         tx.readVal( id )( new ExpanderReader[ S ])
      }

      private class ExpanderReader[ S <: Sys[ S ]] extends TxnReader[ S#Tx, S#Acc, Reactor[ S ]] {
         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
            val targets    = Targets.read( in, access )
            val observers  = targets.childrenVar.get.flatMap( _._2.toObserverKey )
            tx.mapEventTargets( in, access, targets, observers )
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

   final def select( key: Int ) : ReactorSelector[ S ] with ExpandedSelector[ S ] = Selector( key, this )

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

/**
 * A rooted event does not have sources. This trait provides a simple
 * implementation of `pull` which merely checks if this event has fired or not.
 */
trait Root[ S <: Sys[ S ], A ] /* extends Node[ S, A, Repr ] */ {
//   final private[lucre] def events: IIdxSeq[ Event[ S, _, _ ]] = IIdxSeq( this )
//   final private[lucre] def connectSources()( implicit tx: S#Tx ) {}
//   final private[lucre] def disconnectSources()( implicit tx: S#Tx ) {}

   final private[lucre] def connect()(    implicit tx: S#Tx ) {}
   final private[lucre] def disconnect()( implicit tx: S#Tx ) {}

//   final private[lucre] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] =
//      pull( source, update )

//   final /* override */ private[lucre] def pull( /* key: Int, */ source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] = {
//      if( source == this ) Some( update.asInstanceOf[ A ]) else None
//   }

   final /* override */ private[lucre] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ A ] = {
//      require( path.isEmpty )
      Pull( update.asInstanceOf[ A ])
   }
}

/**
 * Value based events fire instances of `Change` which provides the value before
 * and after modification.
 */
final case class Change[ @specialized A ]( before: A, now: A ) {
   def isSignificant: Boolean = before != now
   def toOption: Option[ Change[ A ]] = if( isSignificant ) Some( this ) else None
   def toPull: Pull[ Change[ A ]] = if( isSignificant ) Pull( this ) else EmptyPull
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
//   final private[event] def addReactor(     r: Reactor[ S ])( implicit tx: S#Tx ) {}
//   final private[event] def removeReactor(  r: Reactor[ S ])( implicit tx: S#Tx ) {}

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

   final private[lucre] def select() : ReactorSelector[ S ] with ExpandedSelector[ S ] = node.select( outlet )

   final private[lucre] def isSource( visited: Visited[ S ]) : Boolean = {
      visited.contains( select() )
//      (sel.reactor.id == node.id) && (sel.inlet == outlet)
   }

   protected def reader: Reader[ S, Repr, _ ]
//      implicit protected def serializer: TxnSerializer[ S#Tx, S#Acc, Event[ S, A1, Repr ]]

   final private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
//      node.addReactor( r.select( selector ))
      node.addTarget( outlet, r )
   }

   final private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      node.removeTarget( outlet, r )
   }

   final def react( fun: A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] =
      reactTx( _ => fun )

   final def reactTx( fun: S#Tx => A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] = {
      val res = Observer[ S, A1, Repr ]( reader, fun )
      res.add( this )
      res
   }
}

/**
 * Standalone events unite a node and one particular event.
 */
trait StandaloneLike[ S <: Sys[ S ], A, Repr ] extends Impl[ S, A, A, Repr ] with Invariant[ S, A ]
/* with EarlyBinding[ S, A ] */ /* with Singleton[ S ] with Root[ S, A ] */ {
//   final private[lucre] def select() : Selector[ S ] = Selector( outlet, this )
   final protected def outlet = 1
   final protected def node: Node[ S, A ] = this

   final protected def connectNode()( implicit tx: S#Tx ) { connect() }
   final protected def disconnectNode()( implicit tx: S#Tx ) { disconnect() }

//   final private[lucre] def isSource( sel: ReactorSelector[ S ]) : Boolean = sel.reactor.id == this.id

//   final protected def events: IIdxSeq[ Event[ S, _, _ ]] = IIdxSeq( this )

//   final def pull( key: Int, path: Path[ S ], update: Any )( implicit tx: S#Tx ) : Option[ A ] = pull( path, update )

   final private[lucre] def getEvent( key: Int ) : Event[ S, _ <: A, _ ] = this
}

trait Generator[ S <: Sys[ S ], A, A1 <: A, Repr ] extends Event[ S, A1, Repr ] {
   protected def outlet: Int
   protected def node: Node[ S, A ]

   final protected def fire( update: A1 )( implicit tx: S#Tx ) {
//      val visited: Visited[ S ]  = MMap.empty
      val reactions: Reactions   = Buffer.empty
//      val path: Path[ S ]        = Nil
//      val n                      = node
//      n.propagate( this, update, n, outlet, path, visited, reactions )
//      val reactor = select()
//      reactor.pushUpdate( update, reactor, visited, reactions )

      // important: to be discovered as source (isSource), there must be
      // an entry in the visited map for the generating event, but this
      // must be pointing to an empty set to tell an event that can be
      // both origin and transformer that it actually generated the event!
      val visited: Visited[ S ]  = MMap( (select(), Set.empty) )
      node.children.foreach { tup =>
         val inlet2 = tup._1
         if( inlet2 == outlet /* inlet */) {
            val sel = tup._2
            sel.pushUpdate( update, this, visited, reactions )
         }
      }
      reactions.map( _.apply() ).foreach( _.apply() )
   }
}

object Trigger {
   trait Impl[ S <: Sys[ S ], A, A1 <: A, Repr ] extends Trigger[ S, A1, Repr ] with event.Impl[ S, A, A1, Repr ]
   with Generator[ S, A, A1, Repr ] {
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
   with StandaloneLike[ S, A, Standalone[ S, A ]] with Singleton[ S ] /* with EarlyBinding[ S, A ] */
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
/* with EarlyBinding[ S, Unit ] */ {
   /**
    * A parameterless convenience version of the `Trigger`'s `apply` method.
    */
   def apply()( implicit tx: S#Tx ) { apply( () )}

   override def toString = "Bang"
}

object Mutating {
   object Targets {
      def apply[ S <: Sys[ S ]]( invalid: Boolean )( implicit tx: S#Tx ) : Targets[ S ] = {
         val id         = tx.newID()
         val children   = tx.newVar[ Children[ S ]]( id, IIdxSeq.empty )
         val invalidVar = tx.newBooleanVar( id, invalid )
         new Impl( id, children, invalidVar )
      }

      private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
//         val targets    = read( in, access )
//         val observers  = targets.childrenVar.get.flatMap( _._2.toObserverKey )
//         tx.mapEventTargets( in, access, targets, observers )
         val id         = tx.readID( in, access )
         tx.readVal( id )( new ExpanderReader[ S ])
      }

      private class ExpanderReader[ S <: Sys[ S ]] extends TxnReader[ S#Tx, S#Acc, Reactor[ S ]] {
         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
            val targets    = Targets.read( in, access )
            val observers  = targets.childrenVar.get.flatMap( _._2.toObserverKey )
            tx.mapEventTargets( in, access, targets, observers )
         }
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
         val id: S#ID, protected val childrenVar: S#Var[ Children[ S ]], invalid: S#Var[ Boolean ])
      extends Targets[ S ] {
         def isInvalid( implicit tx: S#Tx ) : Boolean = invalid.get
         def validated()( implicit tx: S#Tx ) { invalid.set( false )}
         def invalidate()( implicit tx: S#Tx ) { invalid.set( true )}

         def write( out: DataOutput ) {
            out.writeUnsignedByte( 1 )
            id.write( out )
            childrenVar.write( out )
            invalid.write( out )
         }

         def dispose()( implicit tx: S#Tx ) {
            require( !isConnected, "Disposing a event reactor which is still being observed" )
            id.dispose()
            childrenVar.dispose()
            invalid.dispose()
         }

         def select( key: Int ) : ReactorSelector[ S ] = Selector( key, this )
      }
   }

   sealed trait Targets[ S <: Sys[ S ]] extends event.Targets[ S ] {
      /* private[event] */ def isInvalid( implicit tx: S#Tx ) : Boolean
//         final def select( key: Int ) : Selector[ S ] = Selector( key, this )
      def invalidate()( implicit tx: S#Tx ) : Unit
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
//            val invalid = targets.isInvalid
//            val res     =
               read( in, access, targets /*, invalid */)
//            if( invalid ) require( !targets.isInvalid, "Reader did not validate structure" )
//            res
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
//   connectNode()

   protected def targets: Mutating.Targets[ S ]

   final def select( key: Int ) : ReactorSelector[ S ] with ExpandedSelector[ S ] = Selector( key, this )

   final private[event] def addTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      targets.add( outlet, sel )
   }

   final private[event] def removeTarget( outlet: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      targets.remove( outlet, sel )
   }

   final def dispose()( implicit tx: S#Tx ) {
      targets.dispose()
      disconnectNode()
      disposeData()
   }
}

/**
 * The sealed `Reactor` trait encompasses the possible targets (dependents) of an event. It defines
 * the `propagate` method which is used in the push-phase (first phase) of propagation. A `Reactor` is
 * either a persisted event `Node` or a registered `ObserverKey` which is resolved through the transaction
 * as pointing to a live view.
 */
//sealed trait Reactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
////   def select( key: Int ) : Selector[ S ]
//   private[event] def propagate( source: Event[ S, _, _ ], update: Any, parent: Node[ S, _ ], key: Int,
//                                 path: Path[ S ], visited: Visited[ S ], reactions: Reactions )( implicit tx: S#Tx ) : Unit
//}

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

object Dummy {
   def apply[ S <: Sys[ S ], A, Repr ] : Dummy[ S, A, Repr ] = new Dummy[ S, A, Repr ] {}

   private def opNotSupported = sys.error( "Operation not supported ")
}
trait Dummy[ S <: Sys[ S ], A, Repr ] extends Event[ S, A, Repr ] {
//   final protected def outlet = 0

   import Dummy._

   final private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {}
   final private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {}

   final private[lucre] def select() : ReactorSelector[ S ] with ExpandedSelector[ S ] = opNotSupported

   /**
    * Returns `false`, as a dummy is never a source event.
    */
   final private[lucre] def isSource( visited: Visited[ S ]) = false

   final def react( fun: A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] =
      Observer.dummy[ S, A, Repr ]

   final def reactTx( fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] =
      Observer.dummy[ S, A, Repr ]

//   final private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] = None
   final private[lucre] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ A ] = {
//      None
      opNotSupported
   }

   final private[lucre] def connect()( implicit tx: S#Tx ) {}
   final private[lucre] def disconnect()( implicit tx: S#Tx ) {}

//   final private[lucre] def lazySources( implicit tx: S#Tx ) : Sources[ S ] = NoSources
}

/**
 * `Event` is not sealed in order to allow you define traits inheriting from it, while the concrete
 * implementations should extend either of `Event.Constant` or `Event.Node` (which itself is sealed and
 * split into `Event.Invariant` and `Event.Mutating`.
 */
trait Event[ S <: Sys[ S ], A, Repr ] /* extends Writer */ {
//   def outlet: Int

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

   def reactTx( fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ]

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
//   private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ]
   private[lucre] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ A ]

   /**
    * Returns a `Selector` (inlet) representation of this event, that is the underlying `Node` along
    * with the inlet identifier corresponding to this event.
    */
   private[lucre] def select() : ReactorSelector[ S ] with ExpandedSelector[ S ]

//   private[lucre] def isSource( sel: ReactorSelector[ S ]) : Boolean
   private[lucre] def isSource( visited: Visited[ S ]) : Boolean

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

//   private[lucre] final def lazySources( implicit tx: S#Tx ) : Sources[ S ] = NoSources

//   final def map[ B >: A, A1 <: B ]( fun: ... )

//   final private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A ] = None
}


object Compound {
//   trait Event[ S <: Sys[ S ], A, Repr ] extends event.Event[ S, A , Repr ] {
//      private[Compound] def
//   }

   private def opNotSupported = sys.error( "Operation not supported" )

   final protected class EventOps1[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B ](
      d: Compound[ S, Repr, D ], e: Event[ S, B, _ ]) {
      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, _ => fun )

      def mapTx[ A1 <: D#Update ]( fun: S#Tx => B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, fun )

//      def |[ Up >: B, C <: Up ]( that: Event[ S, C, _ ]) : EventOr[ S, Repr, D, Up ] =
//         new EventOr[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, _ <: Up, _ ]]( e, that ))
   }

   final protected class EventOps2[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B <: D#Update ](
      d: Compound[ S, Repr, D ], e: Event[ S, B, Repr ]) {
      def |[ Up >: B <: D#Update, C <: Up ]( that: Event[ S, C, Repr ]) : Or[ S, Repr, D, Up ] =
         new Or[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, _ <: Up, Repr ]]( e, that ))
   }

   final class Or[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B <: D#Update ] private[Compound](
      d: Compound[ S, Repr, D ], elems: IIdxSeq[ Event[ S, _ <: B, Repr ]])
//   extends event.Impl[ S, D#Update, B, Repr ] {
   extends Event[ S, B, Repr ] {
//      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]
//
//      private[lucre] def connect()( implicit tx: S#Tx ) {
//         events.foreach( _ ---> this )
//      }
//      private[lucre] def disconnect()( implicit tx: S#Tx ) {
//         events.foreach( _ -/-> this )
//      }

      def react( fun: B => Unit )( implicit tx: S#Tx ) : Observer[ S, B, Repr ] =
         reactTx( _ => fun )

      def reactTx( fun: S#Tx => B => Unit )( implicit tx: S#Tx ) : Observer[ S, B, Repr ] = {
         val obs = Observer( d.decl.serializer, fun )
         elems.foreach( obs add _ )
         obs
      }

      // XXX is this ever invoked? YES YES YES
//      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ B ] = {
//         elems.view.flatMap( _.pull( source, update )).headOption // .map( fun )
//      }
      private[lucre] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ B ] = {
//         opNotSupported
//         elems.view.flatMap( _.pull( path, update )).headOption // .map( fun )
         elems.find( ev => ev.isSource( visited )).flatMap( _.pullUpdate( visited, update ))
      }

      private[lucre] def isSource( visited: Visited[ S ]) : Boolean = opNotSupported

      private[lucre] def select() = opNotSupported

      private[lucre] def connect()( implicit tx: S#Tx ) {}
      private[lucre] def disconnect()( implicit tx: S#Tx ) {}

      private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
         elems.foreach( _ ---> r )
      }
      private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
         elems.foreach( _ -/-> r )
      }

      def |[ Up >: B <: D#Update, C <: Up ]( that: Event[ S, C, Repr ]) : Or[ S, Repr, D, Up ] =
         new Or[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, _ <: Up, Repr ]]( elems: _* ) :+ that )

//      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
//         new OrMap[ S, Repr, D, B, A1 ]( d, elems, fun, d.decl.eventID[ A1 ])

      override def toString = elems.mkString( " | " )
   }

   final protected class CollectionOps[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], Elem <: Node[ S, _ ], B ](
      d: Compound[ S, Repr, D ], elem: Elem => Event[ S, B, Elem ])( implicit elemSer: TxnSerializer[ S#Tx, S#Acc, Elem ]) {

      def map[ A1 <: D#Update ]( fun: IIdxSeq[ B ] => A1 )( implicit m: ClassManifest[ A1 ]) : CollectionEvent[ S, Repr, D, Elem, B, A1 ] =
         new CollectionEvent[ S, Repr, D, Elem, B, A1 ]( d, elem, fun, d.decl.eventID[ A1 ])
   }

   final class CollectionEvent[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], Elem <: Node[ S, _ ], B, A1 <: D#Update ] private[Compound](
      protected val node: Compound[ S, Repr, D ], elemEvt: Elem => Event[ S, B, Elem ], fun: IIdxSeq[ B ] => A1,
      protected val outlet: Int )( implicit elemSer: TxnSerializer[ S#Tx, S#Acc, Elem ], m: ClassManifest[ A1 ])
   extends event.Impl[ S, D#Update, A1, Repr ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]

      override def toString = node.toString + ".collection[" + {
         val mn = m.toString
         val i  = math.max( mn.lastIndexOf( '$' ), mn.lastIndexOf( '.' )) + 1
         mn.substring( i )
      } + "]"

      private[lucre] def connect()( implicit tx: S#Tx ) {}
      private[lucre] def disconnect()( implicit tx: S#Tx ) {}
//      private[lucre] def lazySources( implicit tx: S#Tx ) : Sources[ S ] = NoSources

      def +=( elem: Elem )( implicit tx: S#Tx ) {
         elemEvt( elem ) ---> this
         tx._writeUgly( node.id, elem.id, elem )
      }

      def -=( elem: Elem )( implicit tx: S#Tx ) {
         elemEvt( elem ) -/-> this
      }

      private[lucre] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ A1 ] = {
         val elems: IIdxSeq[ B ] = visited( select() ).flatMap( sel =>
            sel.nodeOption match {
               case Some( nodeSel ) => // this happens for mem-cached and not persisting systems (e.g. `InMemory`)
                  nodeSel.pullUpdate( visited, update ).asInstanceOf[ Option[ B ]]
               case _ =>
                  // this happens for a persisting system (e.g. `BerkeleyDB`).
                  // ; although this is not type enforced (yet), we know that
                  // `Event[ _, _, Elem ]` is represented by a `NodeSelector` with
                  // its node being _represented by_ `Elem`, and thus we know that
                  // at `sel.reactor.id` indeed an `Elem` is stored. Therefore, we
                  // may safely deserialize the element with the given reader, and
                  // can then apply `elemEvt` to get the event/selector.
                  val elem = tx._readUgly[ Elem ]( node.id, sel.reactor.id )
                  elemEvt( elem ).pullUpdate( visited, update ) // we could also do elem.select( sel.inlet ) but would need an additional cast
            }
         )( breakOut )

         if( elems.isEmpty ) None else Some( fun( elems ))
      }
   }

   private final class Map[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B, A1 <: D#Update ](
      protected val node: Compound[ S, Repr, D ], e: Event[ S, B, _ ], fun: S#Tx => B => A1 )( implicit m: ClassManifest[ A1 ])
   extends event.Impl[ S, D#Update, A1, Repr ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]

      protected def outlet = node.decl.eventID[ A1 ]

      private[lucre] def connect()(    implicit tx: S#Tx ) { e ---> this }
      private[lucre] def disconnect()( implicit tx: S#Tx ) { e -/-> this }

//      private[lucre] def lazySources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( e )

      private[lucre] def pullUpdate( visited: Visited[ S ], update: Any )( implicit tx: S#Tx ) : Pull[ A1 ] = {
         e.pullUpdate( visited, update ).map( fun( tx )( _ ))
      }

      override def toString = e.toString + ".map[" + {
         val mn = m.toString
         val i  = math.max( mn.lastIndexOf( '$' ), mn.lastIndexOf( '.' )) + 1
         mn.substring( i )
      } + "]"
   }

//   private final class OrMap[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B, A1 <: D#Update ](
//      protected val node: Compound[ S, Repr, D ], events: IIdxSeq[ Event[ S, _ <: B, _ ]], fun: B => A1,
//      protected val outlet: Int )
//   extends event.Impl[ S, D#Update, A1, Repr ] {
//      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]
//
//      private[lucre] def connect()( implicit tx: S#Tx ) {
//         events.foreach( _ ---> this )
//      }
//      private[lucre] def disconnect()( implicit tx: S#Tx ) {
//         events.foreach( _ -/-> this )
//      }
//
////      private[lucre] def lazySources( implicit tx: S#Tx ) : Sources[ S ] = events
//
//      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ A1 ] = {
//         events.view.flatMap( _.pull( source, update )).headOption.map( fun )
//      }
//   }

   private final class Trigger[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], A1 <: D#Update ](
      protected val node: Compound[ S, Repr, D ])( implicit m: ClassManifest[ A1 ])
   extends event.Trigger.Impl[ S, D#Update, A1, Repr ] with Root[ S, A1 ] {
      protected def reader: Reader[ S, Repr, _ ] = node.decl.serializer // [ S ]

      protected def outlet = node.decl.eventID[ A1 ]

      override def toString = node.toString + ".event[" + {
         val mn = m.toString
         val i  = math.max( mn.lastIndexOf( '$' ), mn.lastIndexOf( '.' )) + 1
         mn.substring( i )
      } + "]"
   }
}
trait Compound[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ]] extends Node[ S, D#Update ] {
   me: Repr =>

   import de.sciss.lucre.{event => evt}

   protected type Ev[ A <: D#Update ] = Event[ S, A, Repr ]

   protected def decl: D // Decl[ Repr ]

   implicit protected def eventOps1[ B ]( e: Event[ S, B, _ ]) : Compound.EventOps1[ S, Repr, D, B ] =
      new Compound.EventOps1( this, e )

   implicit protected def eventOps2[ B <: D#Update ]( e: Event[ S, B, Repr ]) : Compound.EventOps2[ S, Repr, D, B ] =
      new Compound.EventOps2( this, e )

   protected def event[ A1 <: D#Update ]( implicit m: ClassManifest[ A1 ]) : evt.Trigger[ S, A1, Repr ] =
      new Compound.Trigger( this )

   protected def collection[ Elem <: Node[ S, _ ], B ]( fun: Elem => Event[ S, B, Elem ])
                                      ( implicit elemSer: TxnSerializer[ S#Tx, S#Acc, Elem ]) : Compound.CollectionOps[ S, Repr, D, Elem, B ] =
      new Compound.CollectionOps[ S, Repr, D, Elem, B ]( this, fun )

//   final private[event] def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ D#Update ] = {
//      decl.pull( this, key, source, update ) // .asInstanceOf[ Option[ D#Update ]]
//   }

   final private[lucre] def getEvent( key: Int ) : Event[ S, _ <: D#Update, _ ] = decl.getEvent( this, key ) // .asInstanceOf[ Event[ S, D#Update, _ ]]

   final protected def connectNode()( implicit tx: S#Tx ) {
      decl.events( this ).foreach( _.connect() )
   }


   final protected def disconnectNode()( implicit tx: S#Tx ) {
      decl.events( this ).foreach( _.disconnect() )
   }

//   final protected def events = decl.events( this )

//   final protected def connectSources()( implicit tx: S#Tx ) {
//      decl.events( this ).foreach( _.connectSources() )
//   }
//
//   final protected def disconnectSources()( implicit tx: S#Tx ) {
//      decl.events( this ).foreach( _.disconnectSources() )
//   }
}

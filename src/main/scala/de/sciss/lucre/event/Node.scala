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
import stm.{TxnSerializer, Sys, Writer, Disposable}
import annotation.switch
import LucreSTM.logEvent

/**
 * An abstract trait uniting invariant and mutating readers.
 */
/* sealed */ trait Reader[ S <: Sys[ S ], +Repr ] {
   def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Repr
}

trait NodeSerializer[ S <: Sys[ S ], Repr <: /* Writer */ Node[ S ]]
extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
   final def write( v: Repr, out: DataOutput ) { v.write( out )}

   def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
      val targets = Targets.read[ S ]( in, access )
      read( in, access, targets )
   }
}

object Targets {
   private[event] final class ExpanderSerializer[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] {
      def write( v: Reactor[ S ], out: DataOutput ) { v.write( out )}
      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
         val targets    = Targets.read( in, access )
         val observers  = targets.observers
         tx.reactionMap.mapEventTargets( in, access, targets, observers )
      }
   }

   def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
      val id         = tx.newID()
      val children   = tx.newVar[ Children[ S ]]( id, NoChildren )
      val invalid    = tx.newIntVar( id, 0 )
      new Impl( 0, id, children, invalid )
   }

   def partial[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
      val id         = tx.newID()
      val children   = tx.newPartialVar[ Children[ S ]]( id, NoChildren )
      val invalid    = tx.newIntVar( id, 0 )
      new Impl( 1, id, children, invalid )
   }

   private[event] def readAndExpand[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
      val id         = tx.readID( in, access )
      tx.readVal( id )( new ExpanderSerializer[ S ])
   }

   /* private[lucre] */ def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
      (in.readUnsignedByte(): @switch) match {
         case 0      => readIdentified( in, access )
         case 1      => readIdentifiedPartial( in, access )
         case cookie => sys.error( "Unexpected cookie " + cookie )
      }
   }

   private[event] def readIdentified[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
      val id            = tx.readID( in, access )
      val children      = tx.readVar[ Children[ S ]]( id, in )
      val invalid       = tx.readIntVar( id, in )
      new Impl[ S ]( 0, id, children, invalid )
   }

   private[event] def readIdentifiedPartial[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
      val id            = tx.readPartialID( in, access )
      val children      = tx.readPartialVar[ Children[ S ]]( id, in )
      val invalid       = tx.readIntVar( id, in )
      new Impl[ S ]( 1, id, children, invalid )
   }

//   private[event] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ Children[ S ]]) : Targets[ S ] =
//      new EventImpl( id, children )

   private final class Impl[ S <: Sys[ S ]]( cookie: Int,
      val id: S#ID, childrenVar: S#Var[ Children[ S ]], invalidVar: S#Var[ Int ])
   extends Targets[ S ] {
      def write( out: DataOutput ) {
         out.writeUnsignedByte( cookie )
         id.write( out )
         childrenVar.write( out )
         invalidVar.write( out )
      }

      def dispose()( implicit tx: S#Tx ) {
         require( children.isEmpty, "Disposing a event reactor which is still being observed" )
         id.dispose()
         childrenVar.dispose()
         invalidVar.dispose()
      }

      def select( slot: Int, invariant: Boolean ) : ReactorSelector[ S ] = Selector( slot, this, invariant )

      private[event] def children( implicit tx: S#Tx ) : Children[ S ] = childrenVar.get

      override def toString = "Targets" + id

      private[event] def add( slot: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean = {
         logEvent( this.toString + " add( " + slot + ", " + sel + ")" )
         val tup  = (slot, sel)
         val old  = childrenVar.get // .getFresh
         logEvent( this.toString + " old children = " + old )
         sel.writeValue()
         childrenVar.set( old :+ tup )
         !old.exists( _._1 == slot )
      }

      private[event] def remove( slot: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean = {
         logEvent( this.toString + " remove( " + slot + ", " + sel + ")" )
         val tup  = (slot, sel)
         val xs   = childrenVar.get
         logEvent( this.toString + " old children = " + xs )
         val i    = xs.indexOf( tup )
         if( i >= 0 ) {
            val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
            childrenVar.set( xs1 )
   //         xs1.isEmpty
            !xs1.exists( _._1 == slot )
         } else {
            logEvent( this.toString + " selector not found" )
            false
         }
      }

      private[event] def observers( implicit tx: S#Tx ): IIdxSeq[ ObserverKey[ S ]] =
         children.flatMap( _._2.toObserverKey )

      def isEmpty(  implicit tx: S#Tx ) : Boolean = children.isEmpty    // XXX TODO this is expensive
      def nonEmpty( implicit tx: S#Tx ) : Boolean = children.nonEmpty   // XXX TODO this is expensive

//      private[event] def nodeOption : Option[ Node[ S, _ ]] = None
      private[event] def _targets : Targets[ S ] = this

      private[event] def isInvalid( implicit tx: S#Tx ) : Boolean = !invalidVar.isFresh || (invalidVar.get != 0)

      private[event] def isInvalid( slot: Int  )( implicit tx: S#Tx ) : Boolean =
         !invalidVar.isFresh || ((invalidVar.get & slot) != 0)

      private[event] def validated( slot: Int )( implicit tx: S#Tx ) {
         if( invalidVar.isFresh ) {
            invalidVar.transform( _ & ~slot )
         } else {
            invalidVar.set( ~slot )
         }
      }

      private[event] def invalidate( slot: Int )( implicit tx: S#Tx ) {
         if( invalidVar.isFresh ) {
            invalidVar.transform( _ | slot )
         } else {
            invalidVar.set( 0xFFFFFFFF )
         }
      }

      private[event] def validated()( implicit tx: S#Tx ) {
         invalidVar.set( 0 )
      }

      private[event] def invalidate()( implicit tx: S#Tx ) {
         invalidVar.set( 0xFFFFFFFF )
      }
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

//   private[event] def children( implicit tx: S#Tx ) : Children[ S ]

   /**
    * Adds a dependant to this node target.
    *
    * @param slot the slot for this node to be pushing to the dependant
    * @param sel  the target selector to which an event at slot `slot` will be pushed
    *
    * @return  `true` if this was the first dependant registered with the given slot, `false` otherwise
    */
   private[event] def add( slot: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean

   def isEmpty( implicit tx: S#Tx ) : Boolean
   def nonEmpty( implicit tx: S#Tx ) : Boolean

   /**
    * Removes a dependant from this node target.
    *
    * @param slot the slot for this node which is currently pushing to the dependant
    * @param sel  the target selector which was registered with the slot
    *
    * @return  `true` if this was the last dependant unregistered with the given slot, `false` otherwise
    */
   private[event] def remove( slot: Int, sel: ExpandedSelector[ S ])( implicit tx: S#Tx ) : Boolean

   private[event] def observers( implicit tx: S#Tx ): IIdxSeq[ ObserverKey[ S ]]

   private[event] def isInvalid( implicit tx: S#Tx ) : Boolean
   private[event] def validated()( implicit tx: S#Tx ) : Unit
   private[event] def invalidate()( implicit tx: S#Tx ) : Unit

   private[event] def isInvalid( slot: Int  )( implicit tx: S#Tx ) : Boolean
   private[event] def validated( slot: Int )( implicit tx: S#Tx ) : Unit
   private[event] def invalidate( slot: Int )( implicit tx: S#Tx ) : Unit
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
/* sealed */ trait Node[ S <: Sys[ S ]] extends Reactor[ S ] /* with Dispatcher[ S, A ] */ {
   override def toString = "Node" + id

   protected def targets: Targets[ S ]
   protected def writeData( out: DataOutput ) : Unit
   protected def disposeData()( implicit tx: S#Tx ) : Unit

   final protected def validated()( implicit tx: S#Tx ) { targets.validated() }
   final protected def isInvalid( implicit tx: S#Tx ) : Boolean = targets.isInvalid
   final protected def invalidate()( implicit tx: S#Tx ) { targets.invalidate() }

   final private[event] def _targets : Targets[ S ] = targets

   final private[event] def children( implicit tx: S#Tx ) = targets.children

   final def id: S#ID = targets.id

   final def write( out: DataOutput ) {
      targets.write( out )
      writeData( out )
   }

   final def dispose()( implicit tx: S#Tx ) {
      disposeData()     // call this first, as it may release events
      targets.dispose()
   }
}

/**
 * The `Reactor` trait encompasses the possible targets (dependents) of an event. It defines
 * the `propagate` method which is used in the push-phase (first phase) of propagation. A `Reactor` is
 * either a persisted event `Node` or a registered `ObserverKey` which is resolved through the transaction
 * as pointing to a live view.
 */
sealed trait Reactor[ S <: Sys[ S ]] extends /* Reactor[ S ] */ Writer with Disposable[ S#Tx ] {
   def id: S#ID

   private[event] def select( slot: Int, invariant: Boolean ) : ReactorSelector[ S ]

   private[event] def children( implicit tx: S#Tx ) : Children[ S ]

   private[event] def _targets : Targets[ S ]

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ Reactor[ _ ]]) {
         id == that.asInstanceOf[ Reactor[ _ ]].id
      } else super.equals( that ))
   }

   override def hashCode = id.hashCode()
}

/*
 *  Compound.scala
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
import stm.Sys

object Compound {
   private def opNotSupported = sys.error( "Operation not supported" )

   final protected class EventOps1[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], B ](
      d: Compound[ S, Repr, D ], e: Event[ S, B, Any ]) {
      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, _ => fun )

      def mapTx[ A1 <: D#Update ]( fun: S#Tx => B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, fun )

      def mapAndMutate[ A1 <: D#Update ]( fun: S#Tx => B => A1 )( implicit m: ClassManifest[ A1 ]) : MutatingEvent[ S, A1, Repr ] =
         new MutatingMap[ S, Repr, D, B, A1 ]( d, e, fun )
   }

   final protected class EventOps2[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], B /* <: D#Update */ ](
      d: Compound[ S, Repr, D ], e: Event[ S, B, Repr ]) {
      def |[ Up >: B /* <: D#Update */, C <: Up ]( that: Event[ S, C, Repr ]) : Or[ S, Repr, D, Up ] =
         new Or[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, Up, Repr ]]( e, that ))
   }

   final class Or[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], B /* <: D#Update */] private[Compound](
      val node: Compound[ S, Repr, D ], elems: IIdxSeq[ Event[ S, B, Repr ]])
   extends Event[ S, B, Repr ] with InvariantSelector[ S ] {

// XXX
//protected def cookie = opNotSupported
//private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) { opNotSupported }
private[event] def slot = opNotSupported


      def react[ B1 >: B ]( fun: B1 => Unit )( implicit tx: S#Tx ) : Observer[ S, B1, Repr ] =
         reactTx( _ => fun )

      def reactTx[ B1 >: B ]( fun: S#Tx => B1 => Unit )( implicit tx: S#Tx ) : Observer[ S, B1, Repr ] = {
         val obs = Observer( node.decl.serializer, fun )
         elems.foreach( obs.add )
         obs
      }

      /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ B ] = {
         elems.find( ev => ev.isSource( pull )).flatMap( _.pullUpdate( pull ))
      }

      /* private[lucre] */ def isSource( pull: Pull[ S ]) : Boolean = opNotSupported

//      private[lucre] def select() = opNotSupported

      private[lucre] def connect()( implicit tx: S#Tx ) {}
      private[lucre] def reconnect()( implicit tx: S#Tx ) {
//         elems.foreach( _.reconnect() )
      }
      private[lucre] def disconnect()( implicit tx: S#Tx ) {}

      /* private[lucre] */ def --->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {
         elems.foreach( _ ---> r )
      }
      /* private[lucre] */ def -/->( r: /* MMM Expanded */ Selector[ S ])( implicit tx: S#Tx ) {
         elems.foreach( _ -/-> r )
      }

      def |[ Up >: B <: D#Update, C <: Up ]( that: Event[ S, C, Repr ]) : Or[ S, Repr, D, Up ] =
         new Or[ S, Repr, D, Up ]( node, IIdxSeq[ Event[ S, Up, Repr ]]( elems: _* ) :+ that )

      override def toString = elems.mkString( " | " )
   }

   final protected class CollectionOps[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], Elem <: Node[ S ], B ](
      d: Compound[ S, Repr, D ], elem: Elem => EventLike[ S, B, Elem ])( implicit elemReader: Reader[ S, Elem ]) {

      def map[ A1 <: D#Update ]( fun: IIdxSeq[ B ] => A1 )( implicit m: ClassManifest[ A1 ]) : CollectionEvent[ S, Repr, D, Elem, B, A1 ] =
         new CollectionEvent[ S, Repr, D, Elem, B, A1 ]( d, elem, fun )
   }

   sealed trait EventImpl[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], A1 /* <: D#Update */]
   extends event.EventImpl[ S, /* D#Update, */ A1, Repr ] {
      def node: Compound[ S, Repr, D ]
      protected def prefix : String
      implicit protected def m: ClassManifest[ A1 ]

      final protected def reader: Reader[ S, Repr ] = node.decl.serializer // [ S ]

      final private[event] def slot = node.decl.eventID[ A1 ]

      override def toString = prefix + "[" + {
         val mn = m.toString
         val i  = math.max( mn.lastIndexOf( '$' ), mn.lastIndexOf( '.' )) + 1
         mn.substring( i )
      } + "]"
   }

   final class CollectionEvent[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], Elem <: Node[ S ], B, A1 /* <: D#Update */] private[Compound](
      val node: Compound[ S, Repr, D ], elemEvt: Elem => EventLike[ S, B, Elem ], fun: IIdxSeq[ B ] => A1 )
   ( implicit elemReader: Reader[ S, Elem ], protected val m: ClassManifest[ A1 ])
   extends EventImpl[ S, Repr, D, A1 ] with InvariantEvent[ S, A1, Repr ] {

      private[lucre] def connect()( implicit tx: S#Tx ) {}
      private[lucre] def disconnect()( implicit tx: S#Tx ) {}

      def +=( elem: Elem )( implicit tx: S#Tx ) {
         elemEvt( elem ) ---> this
//         tx._writeUgly( reactor.id, elem.id, elem )
      }

      def -=( elem: Elem )( implicit tx: S#Tx ) {
         elemEvt( elem ) -/-> this
      }

      protected def prefix = node.toString + ".event"

      /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A1 ] = {
         val elems: IIdxSeq[ B ] = pull.parents( this /* select() */).flatMap( sel => {
            val elem = sel.devirtualize( elemReader ).node.asInstanceOf[ Elem ]
            elemEvt( elem ).pullUpdate( pull )
         })( breakOut )

         if( elems.isEmpty ) None else Some( fun( elems ))
      }
   }

   private sealed trait MapLike[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], B, A1 /* <: D#Update */]
   extends EventImpl[ S, Repr, D, A1 ] {
      protected def e: Event[ S, B, Any ]

      private[lucre] def connect()(    implicit tx: S#Tx ) { e ---> this }
      private[lucre] def disconnect()( implicit tx: S#Tx ) { e -/-> this }
   }

   private final class Map[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], B, A1 /* <: D#Update */](
      val node: Compound[ S, Repr, D ], protected val e: Event[ S, B, Any ], fun: S#Tx => B => A1 )
   ( implicit protected val m: ClassManifest[ A1 ])
   extends MapLike[ S, Repr, D, B, A1 ] with InvariantEvent[ S, A1, Repr ] {
      /* private[lucre] */ def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A1 ] = {
         e.pullUpdate( pull ).map( fun( tx )( _ ))
      }
      protected def prefix = e.toString + ".map"
   }

   private final class MutatingMap[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], B, A1 /* <: D#Update */](
      val node: Compound[ S, Repr, D ], protected val e: Event[ S, B, Any ], fun: S#Tx => B => A1 )
   ( implicit protected val m: ClassManifest[ A1 ])
   extends MapLike[ S, Repr, D, B, A1 ] with MutatingEvent[ S, A1, Repr  ] {
      protected def processUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A1 ] = {
         e.pullUpdate( pull ).map( fun( tx )( _ ))
      }
      protected def prefix = e.toString + ".mapAndMutate"
   }

   private final class Trigger[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ], A1 <: D#Update ](
      val node: Compound[ S, Repr, D ])( implicit protected val m: ClassManifest[ A1 ])
   extends EventImpl[ S, Repr, D, A1 ] with event.Trigger.Impl[ S, D#Update, A1, Repr ] with Root[ S, A1 ]
   with InvariantEvent[ S, A1, Repr ] {
      protected def prefix = node.toString + ".event"
   }
}
trait Compound[ S <: Sys[ S ], Repr <: NodeSelector[ S, Any ], D <: Decl[ S, Repr ]] extends Node[ S ] {
   me: Repr =>

   import de.sciss.lucre.{event => evt}

   protected type Ev[ A <: D#Update ] = Event[ S, A, Repr ]

   protected def decl: D // Decl[ Repr ]

   implicit protected def eventOps1[ B ]( e: Event[ S, B, Any ]) : Compound.EventOps1[ S, Repr, D, B ] =
      new Compound.EventOps1( this, e )

   implicit protected def eventOps2[ B <: D#Update ]( e: Event[ S, B, Repr ]) : Compound.EventOps2[ S, Repr, D, B ] =
      new Compound.EventOps2( this, e )

   protected def event[ A1 <: D#Update ]( implicit m: ClassManifest[ A1 ]) : evt.Trigger[ S, A1, Repr ] =
      new Compound.Trigger( this )

   protected def collection[ Elem <: Node[ S ], B ]( fun: Elem => EventLike[ S, B, Elem ])
                                      ( implicit elemReader: Reader[ S, Elem ]) : Compound.CollectionOps[ S, Repr, D, Elem, B ] =
      new Compound.CollectionOps[ S, Repr, D, Elem, B ]( this, fun )

   final private[lucre] def select( slot: Int, invariant: Boolean ) : NodeSelector[ S, Any ] = decl.getEvent( this, slot ) // .asInstanceOf[ Event[ S, D#Update, Any ]]
}

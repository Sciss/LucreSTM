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
import stm.{TxnSerializer, Sys}

object Compound {
   private def opNotSupported = sys.error( "Operation not supported" )

   final protected class EventOps1[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B ](
      d: Compound[ S, Repr, D ], e: Event[ S, B, _ ]) {
      def map[ A1 <: D#Update ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, _ => fun )

      def mapTx[ A1 <: D#Update ]( fun: S#Tx => B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr ] =
         new Map[ S, Repr, D, B, A1 ]( d, e, fun )
   }

   final protected class EventOps2[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B <: D#Update ](
      d: Compound[ S, Repr, D ], e: Event[ S, B, Repr ]) {
      def |[ Up >: B <: D#Update, C <: Up ]( that: Event[ S, C, Repr ]) : Or[ S, Repr, D, Up ] =
         new Or[ S, Repr, D, Up ]( d, IIdxSeq[ Event[ S, _ <: Up, Repr ]]( e, that ))
   }

   final class Or[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B <: D#Update ] private[Compound](
      private[event] val reactor: Compound[ S, Repr, D ], elems: IIdxSeq[ Event[ S, _ <: B, Repr ]])
   extends Event[ S, B, Repr ] with InvariantSelector[ S ] {

// XXX
//protected def cookie = opNotSupported
//private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) { opNotSupported }
private[event] def slot = opNotSupported


      def react( fun: B => Unit )( implicit tx: S#Tx ) : Observer[ S, B, Repr ] =
         reactTx( _ => fun )

      def reactTx( fun: S#Tx => B => Unit )( implicit tx: S#Tx ) : Observer[ S, B, Repr ] = {
         val obs = Observer( reactor.decl.serializer, fun )
         elems.foreach( obs add _ )
         obs
      }

      private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ B ] = {
         elems.find( ev => ev.isSource( pull )).flatMap( _.pullUpdate( pull ))
      }

      private[lucre] def isSource( pull: Pull[ S ]) : Boolean = opNotSupported

//      private[lucre] def select() = opNotSupported

      private[lucre] def connect()( implicit tx: S#Tx ) {}
      private[lucre] def disconnect()( implicit tx: S#Tx ) {}

      private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
         elems.foreach( _ ---> r )
      }
      private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
         elems.foreach( _ -/-> r )
      }

      def |[ Up >: B <: D#Update, C <: Up ]( that: Event[ S, C, Repr ]) : Or[ S, Repr, D, Up ] =
         new Or[ S, Repr, D, Up ]( reactor, IIdxSeq[ Event[ S, _ <: Up, Repr ]]( elems: _* ) :+ that )

      override def toString = elems.mkString( " | " )
   }

   final protected class CollectionOps[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], Elem <: Node[ S, _ ], B ](
      d: Compound[ S, Repr, D ], elem: Elem => Event[ S, B, Elem ])( implicit elemSer: TxnSerializer[ S#Tx, S#Acc, Elem ]) {

      def map[ A1 <: D#Update ]( fun: IIdxSeq[ B ] => A1 )( implicit m: ClassManifest[ A1 ]) : CollectionEvent[ S, Repr, D, Elem, B, A1 ] =
         new CollectionEvent[ S, Repr, D, Elem, B, A1 ]( d, elem, fun, d.decl.eventID[ A1 ])
   }

   final class CollectionEvent[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], Elem <: Node[ S, _ ], B, A1 <: D#Update ] private[Compound](
      private[event] val reactor: Compound[ S, Repr, D ], elemEvt: Elem => Event[ S, B, Elem ], fun: IIdxSeq[ B ] => A1,
      private[event] val slot: Int )( implicit elemSer: TxnSerializer[ S#Tx, S#Acc, Elem ], m: ClassManifest[ A1 ])
   extends event.Impl[ S, D#Update, A1, Repr ] with InvariantSelector[ S ] {
      protected def reader: Reader[ S, Repr ] = reactor.decl.serializer // [ S ]

      override def toString = reactor.toString + ".collection[" + {
         val mn = m.toString
         val i  = math.max( mn.lastIndexOf( '$' ), mn.lastIndexOf( '.' )) + 1
         mn.substring( i )
      } + "]"

      private[lucre] def connect()( implicit tx: S#Tx ) {}
      private[lucre] def disconnect()( implicit tx: S#Tx ) {}

      def +=( elem: Elem )( implicit tx: S#Tx ) {
         elemEvt( elem ) ---> this
         tx._writeUgly( reactor.id, elem.id, elem )
      }

      def -=( elem: Elem )( implicit tx: S#Tx ) {
         elemEvt( elem ) -/-> this
      }

      private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A1 ] = {
         val elems: IIdxSeq[ B ] = pull.parents( this /* select() */).flatMap( sel =>
            sel.nodeOption match {
               case Some( nodeSel ) => // this happens for mem-cached and not persisting systems (e.g. `InMemory`)
                  nodeSel.pullUpdate( pull ).asInstanceOf[ Option[ B ]]
               case _ =>
                  // this happens for a persisting system (e.g. `BerkeleyDB`).
                  // ; although this is not type enforced (yet), we know that
                  // `Event[ _, _, Elem ]` is represented by a `NodeSelector` with
                  // its node being _represented by_ `Elem`, and thus we know that
                  // at `sel.reactor.id` indeed an `Elem` is stored. Therefore, we
                  // may safely deserialize the element with the given reader, and
                  // can then apply `elemEvt` to get the event/selector.
                  val elem = tx._readUgly[ Elem ]( reactor.id, sel.reactor.id )
                  elemEvt( elem ).pullUpdate( pull ) // we could also do elem.select( sel.slot ) but would need an additional cast
            }
         )( breakOut )

         if( elems.isEmpty ) None else Some( fun( elems ))
      }
   }

   private final class Map[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], B, A1 <: D#Update ](
      private[event] val reactor: Compound[ S, Repr, D ], e: Event[ S, B, _ ], fun: S#Tx => B => A1 )( implicit m: ClassManifest[ A1 ])
   extends event.Impl[ S, D#Update, A1, Repr ] with InvariantSelector[ S ] {
      protected def reader: Reader[ S, Repr ] = reactor.decl.serializer // [ S ]

      private[event] def slot = reactor.decl.eventID[ A1 ]

      private[lucre] def connect()(    implicit tx: S#Tx ) { e ---> this }
      private[lucre] def disconnect()( implicit tx: S#Tx ) { e -/-> this }

      private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A1 ] = {
         e.pullUpdate( pull ).map( fun( tx )( _ ))
      }

      override def toString = e.toString + ".map[" + {
         val mn = m.toString
         val i  = math.max( mn.lastIndexOf( '$' ), mn.lastIndexOf( '.' )) + 1
         mn.substring( i )
      } + "]"
   }

   private final class Trigger[ S <: Sys[ S ], Repr, D <: Decl[ S, Repr ], A1 <: D#Update ](
      private[event] val reactor: Compound[ S, Repr, D ])( implicit m: ClassManifest[ A1 ])
   extends event.Trigger.Impl[ S, D#Update, A1, Repr ] with Root[ S, A1 ]
   with InvariantSelector[ S ] {
      protected def reader: Reader[ S, Repr ] = reactor.decl.serializer // [ S ]

      private[event] def slot = reactor.decl.eventID[ A1 ]

      override def toString = reactor.toString + ".event[" + {
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

//   final private[lucre] def getEvent( slot: Int ) : Event[ S, _ <: D#Update, _ ] = decl.getEvent( this, slot ) // .asInstanceOf[ Event[ S, D#Update, _ ]]

//   final private[event] def select( slot: Int ) : NodeSelector[ S, D#Update ] = decl.getEvent( this, slot )

//   final protected def connectNode()( implicit tx: S#Tx ) {
//      decl.events( this ).foreach( _.connect() )
//   }
//
//
//   final protected def disconnectNode()( implicit tx: S#Tx ) {
//      decl.events( this ).foreach( _.disconnect() )
//   }
}

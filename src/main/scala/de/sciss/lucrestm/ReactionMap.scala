/*
 *  ReactionMap.scala
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

package de.sciss.lucrestm

import concurrent.stm.TMap
import collection.immutable.{IndexedSeq => IIdxSeq}

object ReactionMap {
   type Reaction  = () => () => Unit
   type Reactions = IIdxSeq[ Reaction ]

   private val noOpEval                   = () => ()
   private type AnyObsFun[ S <: Sys[ S ]] =  (S#Tx, AnyRef) => Unit

   def apply[ S <: Sys[ S ], T <: Sys[ T ]]( cnt: T#Var[ Int ])( implicit sysConv: S#Tx => T#Tx ) : ReactionMap[ S ] =
      new Impl[ S, T ]( cnt )

   private final case class StateObservation[ S <: Sys[ S ], A, Repr <: State[ S, A ]](
      reader: State.Reader[ S, Repr ], fun: (S#Tx, A) => Unit )

   private final case class EventObservation[ S <: Sys[ S ], A, Repr <: Event[ S, A, _ ]](
      reader: Event.Reader[ S, Repr, _ ], fun: (S#Tx, A) => Unit )

   private final class Impl[ S <: Sys[ S ], T <: Sys[ T ]]( cnt: T#Var[ Int ])( implicit sysConv: S#Tx => T#Tx )
   extends ReactionMap[ S ] {
      private val stateMap = TMap.empty[ Int, StateObservation[ S, _, _ <: State[ S, _ ]]]

      private val eventMap = TMap.empty[ Int, EventObservation[ S, _, _ <: Event[ S, _, _ ]]]

      def mapEventTargets( in: DataInput, access: S#Acc, targets: Event.Targets[ S ],
                           observers: IIdxSeq[ Event.ObserverKey[ S ]])
                         ( implicit tx: S#Tx ) : Event.Reactor[ S ] = {
         val itx = tx.peer
         val observations = observers.flatMap( k => eventMap.get( k.id )( itx ))
         observations.headOption match {
            case Some( obs ) => obs.reader.asInstanceOf[ Event.Reader[ S, Event.Reactor[ S ], Event.Targets[ S ]]]   // ugly XXX
               .read( in, access, targets )
            case None => targets
         }
      }

      def propagateEvent( observer: Event.ObserverKey[ S ], source: Event[ S, _, _ ], update: Any,
                          leaf: Event.Node[ S, _ ], selector: Int, /* visited: Event.Visited[ S ], */
                          reactions: Event.Reactions )( implicit tx: S#Tx ) : Event.Reactions = {
         val itx = tx.peer
         eventMap.get( observer.id )( itx ) match {
            case Some( obs ) =>
               val react: Reaction = () => {
                  leaf.pull( selector, source, update ) match {
                     case Some( result ) =>
                        () => obs.fun.asInstanceOf[ AnyObsFun[ S ]].apply( tx, result.asInstanceOf[ AnyRef ])
                     case None => noOpEval
                  }
               }
               reactions :+ react

            case None => reactions
         }
      }

      def addEventReaction[ A, Repr /* <: Event[ S, A ] */]( reader: Event.Reader[ S, Repr, _ ], fun: (S#Tx, A) => Unit )
                                                     ( implicit tx: S#Tx ) : Event.ObserverKey[ S ] = {
         val ttx = sysConv( tx )
         val key = cnt.get( ttx )
         cnt.set( key + 1 )( ttx )
//         eventMap.+=( (key, new EventObservation[ S, A, Repr ]( reader, fun )) )( tx.peer )
         sys.error( "TODO" )  // UUU
         new Event.ObserverKey[ S ]( key )
      }

      def removeEventReaction( key: Event.ObserverKey[ S ])( implicit tx: S#Tx ) {
         eventMap.-=( key.id )( tx.peer )
      }

      def mapStateTargets( in: DataInput, access: S#Acc, targets: State.Targets[ S ], observerKeys: IIdxSeq[ Int ])
                         ( implicit tx: S#Tx ) : State.Reactor[ S ] = {
         val itx = tx.peer
         val observations = observerKeys.flatMap( stateMap.get( _ )( itx ))
         observations.headOption match {
            case Some( obs ) => obs.reader.read( in, access, targets ).asInstanceOf[ State.Reactor[ S ]] // ugly XXX
            case None => targets
         }
      }

      def propagateState( key: Int, state: State[ S, _ ], reactions: State.Reactions )
                  ( implicit tx: S#Tx ) : State.Reactions = {
         val itx = tx.peer
         stateMap.get( key )( itx ) match {
            case Some( obs ) =>
               val react: Reaction = () => {
                  val eval = state.value.asInstanceOf[ AnyRef ]
                  () => obs.fun.asInstanceOf[ AnyObsFun[ S ]]( tx, eval )
               }
               reactions :+ react

            case None => reactions
         }
      }

      def addStateReaction[ A, Repr <: State[ S, A ]]( reader: State.Reader[ S, Repr ],
                                                       fun: (S#Tx, A) => Unit )
                                                     ( implicit tx: S#Tx ) : State.ReactorKey[ S ] = {
         val ttx = sysConv( tx )
         val key = cnt.get( ttx )
         cnt.set( key + 1 )( ttx )
         stateMap.+=( (key, new StateObservation[ S, A, Repr ]( reader, fun )) )( tx.peer )
         new State.ReactorKey[ S ]( key )
      }

      def removeStateReaction( key: State.ReactorKey[ S ])( implicit tx: S#Tx ) {
         stateMap.-=( key.key )( tx.peer )
      }
   }
}
trait ReactionMap[ S <: Sys[ S ]] {
   def addStateReaction[ A, Repr <: State[ S, A ]]( reader: State.Reader[ S, Repr ], fun: (S#Tx, A) => Unit )
                                                  ( implicit tx: S#Tx ) : State.ReactorKey[ S ]

   def removeStateReaction( key: State.ReactorKey[ S ])( implicit tx: S#Tx ) : Unit

   def mapStateTargets( in: DataInput, access: S#Acc, targets: State.Targets[ S ], observerKeys: IIdxSeq[ Int ])
               ( implicit tx: S#Tx ) : State.Reactor[ S ]

   def propagateState( key: Int, state: State[ S, _ ], reactions: State.Reactions )
                     ( implicit tx: S#Tx ) : State.Reactions

   def addEventReaction[ A, Repr /* <: Event[ S, A ] */]( reader: Event.Reader[ S, Repr, _ ], fun: (S#Tx, A) => Unit )
                                                  ( implicit tx: S#Tx ) : Event.ObserverKey[ S ]

   def removeEventReaction( key: Event.ObserverKey[ S ])( implicit tx: S#Tx ) : Unit

   def mapEventTargets( in: DataInput, access: S#Acc, targets: Event.Targets[ S ], observer: IIdxSeq[ Event.ObserverKey[ S ]])
                      ( implicit tx: S#Tx ) : Event.Reactor[ S ]

//   def propagateEvent( observer: Event.ObserverKey[ S ], visited: Event.Visited[ S ], leaf: Event.Node[ S, _ ], reactions: Event.Reactions )
//                     ( implicit tx: S#Tx ) : Event.Reactions

   def propagateEvent( observer: Event.ObserverKey[ S ], source: Event[ S, _, _ ], update: Any,
                       leaf: Event.Node[ S, _ ], selector: Int, /* visited: Event.Visited[ S ], */
                       reactions: Event.Reactions )( implicit tx: S#Tx ) : Event.Reactions
}

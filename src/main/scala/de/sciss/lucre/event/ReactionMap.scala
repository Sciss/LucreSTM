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

package de.sciss.lucre
package event

import concurrent.stm.TMap
import stm.Sys
import collection.immutable.{IndexedSeq => IIdxSeq}

object ReactionMap {
//   type Reaction  = () => () => Unit
////   type Reactions = IIdxSeq[ Reaction ]
//   type Reactions = Buffer[ Reaction ]

   private val noOpEval                   = () => ()
   private type AnyObsFun[ S <: Sys[ S ]] =  S#Tx => AnyRef => Unit

   def apply[ S <: Sys[ S ], T <: Sys[ T ]]( cnt: T#Var[ Int ])( implicit sysConv: S#Tx => T#Tx ) : ReactionMap[ S ] =
      new Impl[ S, T ]( cnt )

//   private final case class StateObservation[ S <: Sys[ S ], A, Repr <: State[ S, A ]](
//      reader: State.Reader[ S, Repr ], fun: (S#Tx, A) => Unit )

   private final case class EventObservation[ S <: Sys[ S ], -A, +Repr <: NodeSelector[ S, Any ]](
      reader: event.Reader[ S, Repr ], fun: S#Tx => A => Unit )

   private final class Impl[ S <: Sys[ S ], T <: Sys[ T ]]( cnt: T#Var[ Int ])( implicit sysConv: S#Tx => T#Tx )
   extends ReactionMap[ S ] {
//      private val stateMap = TMap.empty[ Int, StateObservation[ S, _, _ <: State[ S, _ ]]]

      private val eventMap = TMap.empty[ Int, EventObservation[ S, _, _ ]]

//      def mapEventTargets( in: DataInput, access: S#Acc, targets: Targets[ S ],
//                           observers: IIdxSeq[ ObserverKey[ S ]])
//                         ( implicit tx: S#Tx ) : Reactor[ S ] = {
//         val itx = tx.peer
//         val observations = observers.flatMap( k => eventMap.get( k.id )( itx ))
//         observations.headOption match {
//            case Some( obs ) => obs.reader.asInstanceOf[ event.Reader[ S, Reactor[ S ]]]   // ugly XXX
//               .read( in, access, targets )
//            case None => targets
//         }
//      }

      def processEvent( leaf: ObserverKey[ S ], parent: VirtualNodeSelector[ S ], push: Push[ S ])( implicit tx: S#Tx ) {
         val itx = tx.peer
         eventMap.get( leaf.id )( itx ).foreach { obs =>
            val nParent = parent.devirtualize( obs.reader.asInstanceOf[ Reader[ S, Node[ S ]]]) // ugly XXX
            val react: Reaction = () => {
               nParent.pullUpdate( push ) match {
                  case Some( result ) =>
                     () => obs.fun.asInstanceOf[ AnyObsFun[ S ]]( tx )( result.asInstanceOf[ AnyRef ])
                  case None => noOpEval
               }
            }
            push.addReaction( react )
         }
      }

      def addEventReaction[ A, Repr <: NodeSelector[ S, Any ]]( reader: event.Reader[ S, Repr ], fun: S#Tx => A => Unit )
                                                           ( implicit tx: S#Tx ) : ObserverKey[ S ] = {
         val ttx = sysConv( tx )
         val key = cnt.get( ttx )
         cnt.set( key + 1 )( ttx )
         eventMap.+=( (key, new EventObservation[ S, A, Repr ]( reader, fun )) )( tx.peer )
         new ObserverKey[ S ]( key )
      }

      def removeEventReaction( key: ObserverKey[ S ])( implicit tx: S#Tx ) {
         eventMap.-=( key.id )( tx.peer )
      }
   }
}

trait ReactionMap[ S <: Sys[ S ]] {
   def addEventReaction[ A, Repr <: NodeSelector[ S, Any ]]( reader: event.Reader[ S, Repr ], fun: S#Tx => A => Unit )
                                                  ( implicit tx: S#Tx ) : ObserverKey[ S ]

   def removeEventReaction( key: ObserverKey[ S ])( implicit tx: S#Tx ) : Unit

//   def mapEventTargets( in: DataInput, access: S#Acc, targets: Targets[ S ], observer: IIdxSeq[ ObserverKey[ S ]])
//                      ( implicit tx: S#Tx ) : Reactor[ S ]

   def processEvent( leaf: ObserverKey[ S ], parent: VirtualNodeSelector[ S ], push: Push[ S ])( implicit tx: S#Tx ) : Unit
}

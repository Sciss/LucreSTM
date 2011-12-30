/*
 *  ReactionMap.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011 Hanns Holger Rutz. All rights reserved.
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

   def apply[ S <: Sys[ S ], T <: Sys[ T ]]( cnt: T#Var[ Int ])( implicit sysConv: S#Tx => T#Tx ) : ReactionMap[ S ] =
      new Impl[ S, T ]( cnt )

//   private final case class Observation[ Txn, A ]( reader: A, fun: (Txn, A) => Unit )

   private final case class Observation[ S <: Sys[ S ], A, Repr <: State[ S, A, Repr ]](
      reader: StateReader[ S, Repr ], fun: (S#Tx, A) => Unit ) {

//      def perform()
   }

   private final class Impl[ S <: Sys[ S ], T <: Sys[ T ]]( cnt: T#Var[ Int ])( implicit sysConv: S#Tx => T#Tx )
   extends ReactionMap[ S ] {
//      private val stateMap = TMap.empty[ Int, (TxnReader[ S#Tx, S#Acc, State[ S, _, _ ]], (S#Tx, _) => Unit) ]
      private val stateMap = TMap.empty[ Int, Observation[ S, _, _ <: State[ S, _, _]]]
//      private val eventMap = TMap.empty[ Int, S#Tx => Unit ]

//      def invokeState( leaf: StateReactorLeaf[ S ])( implicit tx: S#Tx ) {
//////         stateMap.get( leaf.id )( tx.peer ).foreach( _.apply( tx ))
////         sys.error( "TODO")
//      }

      def mapStateTargets( in: DataInput, targets: StateTargets[ S ], observerKeys: IIdxSeq[ Int ], reactions: Reactions )
                         ( implicit tx: S#Tx ) : Reactions = {
         val itx = tx.peer
         val observations = observerKeys.flatMap( stateMap.get( _ )( itx ))
         observations.headOption match {
            case Some( obs ) =>
               val full = obs.reader.read( in, targets ).asInstanceOf[ State[ S, AnyRef, _ <: State[ S, AnyRef, _ ]]]
               val funs = observations.map( _.fun ).asInstanceOf[ IIdxSeq[ (S#Tx, AnyRef) => Unit ]]
               val react: Reaction = () => {
                  val eval = full.get
                  () => funs.foreach( _.apply( tx, eval ))
               }
               reactions :+ react

            case None => reactions
         }
      }

      def addState[ A, Repr <: State[ S, A, Repr ]]( /* source: Repr, */ reader: StateReader[ S, Repr ],
                                                     fun: (S#Tx, A) => Unit )
                                                   ( implicit tx: S#Tx ) : Int /* Disposable[ S#Tx ] */ = {
         val ttx = sysConv( tx )
         val key = cnt.get( ttx )
         cnt.set( key + 1 )( ttx )
         stateMap.+=( (key, new Observation[ S, A, Repr ]( reader, fun )) )( tx.peer )
//         source.addObserver( key )
////         new StateReactorLeaf[ S ]( key )
//         new Disposable[ S#Tx ] {
//            def dispose()( implicit tx: S#Tx ) {
//println( "XXX addState.dispose -- dunno what to do yet XXX" )
//            }
//         }
         key
      }

//      def addState( fun: S#Tx => Unit )( implicit tx: S#Tx ) : StateReactorLeaf[ S ] = {
//         val ttx = sysConv( tx )
//         val key = cnt.get( ttx )
//         cnt.set( key + 1 )( ttx )
//         stateMap.+=( (key, fun) )( tx.peer )
//         new StateReactorLeaf[ S ]( key )
//      }

//      def removeState( leaf: StateReactorLeaf[ S ])( implicit tx: S#Tx ) {
//         stateMap.-=( leaf.id )( tx.peer )
//      }
   }
}
sealed trait ReactionMap[ S <: Sys[ S ]] {
//   def addState( reaction: S#Tx => Unit )( implicit tx: S#Tx ) : StateReactorLeaf[ S ]
   def addState[ A, Repr <: State[ S, A, Repr ]]( /* source: Repr, */ reader: StateReader[ S, Repr ], fun: (S#Tx, A) => Unit )
                                                ( implicit tx: S#Tx ) : Int // Disposable[ S#Tx ]

//   def removeState( leaf: StateReactorLeaf[ S ])( implicit tx: S#Tx ) : Unit

   def mapStateTargets( in: DataInput, targets: StateTargets[ S ], observerKeys: IIdxSeq[ Int ],
                        reactions: ReactionMap.Reactions )
                      ( implicit tx: S#Tx ) : ReactionMap.Reactions

//   def invokeState( leaf: StateReactorLeaf[ S ])( implicit tx: S#Tx ) : Unit

//   def addEvent( reaction: S#Tx => Unit )( implicit tx: S#Tx ) : EventReactorLeaf[ S ]
//   def removeEvent( leaf: EventReactorLeaf[ S ])( implicit tx: S#Tx ) : Unit
//   def invokeEvent( leaf: EventReactorLeaf[ S ], key: Int )( implicit tx: S#Tx ) : Unit
}

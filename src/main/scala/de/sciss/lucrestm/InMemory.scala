/*
 *  InMemory.scala
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

import de.sciss.lucrestm.{Var => _Var, Txn => _Txn}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}
import collection.immutable.{IndexedSeq => IIdxSeq}

object InMemory {
   private type S = InMemory

   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ]

   private sealed trait SourceImpl[ @specialized A ] {
      protected def peer: ScalaRef[ A ]
      def get( implicit tx: Txn ) : A = peer.get( tx.peer )
      def write( out: DataOutput ) {}
   }

   private final class VarImpl[ @specialized A ]( protected val peer: ScalaRef[ A ])
   extends Var[ A ] with SourceImpl[ A ] {
      override def toString = "Var<" + hashCode().toHexString + ">"

      def set( v: A )( implicit tx: Txn ) { peer.set( v )( tx.peer )}
      def transform( f: A => A )( implicit tx: Txn ) { peer.transform( f )( tx.peer )}
      def dispose()( implicit tx: Txn ) { peer.set( null.asInstanceOf[ A ])( tx.peer )}
   }

//   private final class ObsVarImpl[ @specialized A ]( protected val peer: ScalaRef[ A ])
//   extends Var[ A ] with State[ InMemory, Change[ A ]] with SourceImpl[ A ] {
//      override def toString = "ObsVar<" + hashCode().toHexString + ">"
//
//      private type Obs = Observer[ Txn, Change[ A ]]
//
//      private val obs = ScalaRef[ IIdxSeq[ Obs ]]( IIdxSeq.empty )
//
//      def set( now: A )( implicit tx: Txn ) {
//         val before = peer.swap( now )( tx.peer )
//         if( before != now ) {
//            notifyObservers( new Change( before, now ))
//         }
//      }
//
//      def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}
//
//      def dispose()( implicit tx: Txn ) {
//         implicit val itx = tx.peer
//         require( obs.get.isEmpty, "Disposing a var which is still observed" )
//         peer.set( null.asInstanceOf[ A ])
//      }
//
//      def notifyObservers( change: Change[ A ])( implicit tx: Txn ) {
//         obs.get( tx.peer ).foreach( _.update( change ))
//      }
//
//      def addObserver( o: Obs )( implicit tx: Txn ) {
//         obs.transform( _ :+ o )( tx.peer )
//      }
//
//      def removeObserver( o: Obs )( implicit tx: Txn ) {
////         obs.transform( _.filterNot( _ == o ))( tx.peer )
//         obs.transform({ seq =>
//            val i             = seq.indexOf( o )
//            if( i == 0 ) {
//               seq.tail
//            } else if( i == seq.size - 1 ) {
//               seq.init
//            } else {
//               val (left, right) = seq.splitAt( i )
//               left ++ right.tail
//            }
//         })( tx.peer )
//      }
//   }

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   sealed trait ID extends Identifier[ Txn ]

   private final class IDImpl extends ID {
      def write( out: DataOutput ) {}
      def dispose()( implicit tx: Txn ) {}
      override def toString = "<" + hashCode().toHexString + ">"
   }

   sealed trait Txn extends _Txn[ S ]

   private final class TxnImpl( val system: System, val peer: InTxn ) extends Txn {
      def newID() : ID = new IDImpl

      def addStateReaction[ A, Repr <: State[ S, A ]](
         reader: State.Reader[ S, Repr ], fun: (Txn, A) => Unit ) : State.ReactorKey[ S ] =
            system.reactionMap.addStateReaction( reader, fun )( this )

      def mapStateTargets( in: DataInput, access: S#Acc, targets: State.Targets[ S ],
                                               keys: IIdxSeq[ Int ]) : State.Reactor[ S ] =
         system.reactionMap.mapStateTargets( in, access, targets, keys )( this )

      def propagateState( key: Int, state: State[ S, _ ],
                                            reactions: State.Reactions ) : State.Reactions =
         system.reactionMap.propagateState( key, state, reactions )( this )

      def removeStateReaction( key: State.ReactorKey[ S ]) { system.reactionMap.removeStateReaction( key )( this )}

      def addEventReaction[ A, Repr /* <: Event[ S, A ] */]( reader: Event.Reader[ S, Repr, _ ],
                                                       fun: (S#Tx, A) => Unit ) : Event.ObserverKey[ S ] =
         system.reactionMap.addEventReaction( reader, fun )( this )

      def mapEventTargets( in: DataInput, access: S#Acc, targets: Event.Targets[ S ],
                           keys: IIdxSeq[ Int ]) : Event.Reactor[ S ] =
         system.reactionMap.mapEventTargets( in, access, targets, keys )( this )

//      def propagateEvent( key: Int, source: Event.Posted[ S, _ ], state: Event[ S, _ ], reactions: Event.Reactions ) : Event.Reactions =
//         system.reactionMap.propagateEvent( key, source, state, reactions )( this )

      def removeEventReaction( key: Event.ObserverKey[ S ]) { system.reactionMap.removeEventReaction( key )( this )}

      def newVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newIntVar( id: ID, init: Int ) : Var[ Int ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newBooleanVar( id: ID, init: Boolean ) : Var[ Boolean ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

      def newLongVar( id: ID, init: Long ) : Var[ Long ] = {
         val peer = ScalaRef( init )
         new VarImpl( peer )
      }

//      def newObservableVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : ObsVar[ A ] = {
//         val peer = ScalaRef( init )
//         new ObsVarImpl( peer )
//      }
//
//      def newObservableIntVar( id: ID, init: Int ) : ObsVar[ Int ] = {
//         val peer = ScalaRef( init )
//         new ObsVarImpl( peer )
//      }

      def newVarArray[ A ]( size: Int ) = new Array[ Var[ A ]]( size )

      def readVar[ A ]( id: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         opNotSupported( "readVar" )
      }

      def readBooleanVar( id: ID, in: DataInput ) : Var[ Boolean ] = {
         opNotSupported( "readBooleanVar" )
      }

      def readIntVar( id: ID, in: DataInput ) : Var[ Int ] = {
         opNotSupported( "readIntVar" )
      }

      def readLongVar( id: ID, in: DataInput ) : Var[ Long ] = {
         opNotSupported( "readLongVar" )
      }

//      def readObservableVar[ A ]( id: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : ObsVar[ A ] = {
//         opNotSupported( "readObservableVar" )
//      }
//
//      def readObservableIntVar( id: ID, in: DataInput ) : ObsVar[ Int ] = {
//         opNotSupported( "readObservableIntVar" )
//      }

      def readID( in: DataInput, acc: Unit ) : ID = opNotSupported( "readID" )

//      def readMut[ A <: Mutable[ S ]]( id: ID, in: DataInput )
//                                            ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
//         opNotSupported( "readMut" )
//      }
//
//      def readOptionMut[ A <: MutableOption[ S ]]( id: ID, in: DataInput )
//                                                        ( implicit reader: MutableOptionReader[ ID, Txn, A ]) : A = {
//         opNotSupported( "readOptionMut" )
//      }
   }

   private final class System extends InMemory {
      def manifest: Manifest[ S ] = Manifest.classType( classOf[ InMemory ])

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, S ]( new VarImpl( ScalaRef( 0 )))

      def atomic[ Z ]( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ]( itx => block( new TxnImpl( this, itx )))
      }

      private[lucrestm] def wrap( itx: InTxn ) : Tx = new TxnImpl( this, itx )
   }

   def apply() : S = new System
}

/**
 * A thin wrapper around scala-stm.
 */
sealed trait InMemory extends Sys[ InMemory ] {
//   import InMemory._

   type Var[ @specialized A ] = InMemory.Var[ A ]
   type ID                    = InMemory.ID
   type Tx                    = InMemory.Txn
   type Acc                   = Unit

   private[lucrestm] def wrap( itx: InTxn ) : Tx
}
/*
 *  StateReactor.scala
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

import collection.immutable.{IndexedSeq => IIdxSeq}

object StateReactor {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, StateReactor[ S ]] = new Ser[ S ]

   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, StateReactor[ S ]] {
      def write( r: StateReactor[ S ], out: DataOutput ) { r.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : StateReactor[ S ] = {
         if( in.readUnsignedByte() == 0 ) {
            new StateReactorBranchStub[ S ]( in, tx, access )
         } else {
            new StateReactorLeaf[ S ]( in.readInt() )
         }
      }
   }

   private final class StateReactorBranchStub[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc )
   extends StateReactorBranchLike[ S ] {
      val id = tx0.readID( in, acc )
      protected val children = tx0.readVar[ IIdxSeq[ StateReactor[ S ]]]( id, in )
   }
}

sealed trait StateReactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
   def propagate()( implicit tx: S#Tx ) : Unit
}

//object StateReactorLeaf {
//   def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : StateReactorLeaf[ S ] = new StateReactorLeaf( map.newID() )
//}

final case class StateReactorLeaf[ S <: Sys[ S ]] private[lucrestm]( id: Int ) extends StateReactor[ S ] {
   def write( out: DataOutput ) {
      out.writeUnsignedByte( 1 )
      out.writeInt( id )
   }

   def propagate()( implicit tx: S#Tx ) {
      tx.invokeStateReaction( this )
   }

   def dispose()( implicit tx: S#Tx ) {
      tx.removeStateReaction( this )
   }
}

object State {
   def constant[ S <: Sys[ S ]] : State[ S ] = new Const[ S ]

   private final class Const[ S <: Sys[ S ]] extends State[ S ] {
      def addReactor(    r: StateReactor[ S ])( implicit tx: S#Tx ) {}
      def removeReactor( r: StateReactor[ S ])( implicit tx: S#Tx ) {}
   }
}

trait State[ S <: Sys[ S ]] {
   def addReactor(    r: StateReactor[ S ])( implicit tx: S#Tx ) : Unit
   def removeReactor( r: StateReactor[ S ])( implicit tx: S#Tx ) : Unit
}

object StateSources {
   def none[ S <: Sys[ S ]] : StateSources[ S ] = new NoSources[ S ]

   private final class NoSources[ S <: Sys[ S ]] extends StateSources[ S ] {
      def stateSources( implicit tx: S#Tx ) : IIdxSeq[ State[ S ]] = IIdxSeq.empty
   }
}

trait StateSources[ S <: Sys[ S ]] {
   def stateSources( implicit tx: S#Tx ) : IIdxSeq[ State[ S ]]
}

object StateReactorBranch {
   def apply[ S <: Sys[ S ]]( sources: StateSources[ S ])( implicit tx: S#Tx ) : StateReactorBranch[ S ] =
      new New[ S ]( sources, tx )

   private final class New[ S <: Sys[ S ]]( protected val sources: StateSources[ S ], tx0: S#Tx )
   extends StateReactorBranch[ S ] {
      val id = tx0.newID()
      protected val children = tx0.newVar[ IIdxSeq[ StateReactor[ S ]]]( id, IIdxSeq.empty )
   }

//      def serializer[ S <: Sys[ S ]]: TxnSerializer[ S#Tx, S#Acc, StateReactorBranch[ S ]] =
//         new TxnSerializer[ S#Tx, S#Acc, StateReactorBranch[ S ]] {
//            def write( r: StateReactorBranch[ S ], out: DataOutput ) { r.write( out )}
//            def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : StateReactorBranch[ S ] = {
//               require( in.readUnsignedByte() == 0 )
//               new ReactorBranchRead( in, access, tx )
//            }
//         }

   def read[ S <: Sys[ S ]]( sources: StateSources[ S ], in: DataInput, access: S#Acc )
                           ( implicit tx: S#Tx ) : StateReactorBranch[ S ] = {
      require( in.readUnsignedByte() == 0 )
      new Read[ S ]( sources, in, access, tx )
   }

   private final class Read[ S <: Sys[ S ]]( protected val sources: StateSources[ S ],
                                             in: DataInput, access: S#Acc, tx0: S#Tx )
   extends StateReactorBranch[ S ] {
      val id = tx0.readID( in, access )
      protected val children = tx0.readVar[ IIdxSeq[ StateReactor[ S ]]]( id, in )
   }
}

sealed trait StateReactorBranchLike[ S <: Sys[ S ]] extends StateReactor[ S ] {
   def id: S#ID
   protected def children: S#Var[ IIdxSeq[ StateReactor[ S ]]]

   final def isConnected( implicit tx: S#Tx ) : Boolean = children.get.nonEmpty

   final def propagate()( implicit tx: S#Tx ) {
      children.get.foreach( _.propagate() )
   }

   final def write( out: DataOutput ) {
      out.writeUnsignedByte( 0 )
      id.write( out )
      children.write( out )
   }

   final def dispose()( implicit tx: S#Tx ) {
      require( !isConnected )
      id.dispose()
      children.dispose()
   }

   override def toString = "StateReactorBranch" + id

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ StateReactorBranchLike[ _ ]]) {
         id == that.asInstanceOf[ StateReactorBranchLike[ _ ]].id
      } else super.equals( that ))
   }

   override def hashCode = id.hashCode()
}

/**
 * A `StateReactorBranch` is most similar to EScala's `EventNode` class. It represents an observable
 * object and can also act as an observer itself.
 */
sealed trait StateReactorBranch[ S <: Sys[ S ]] extends StateReactorBranchLike[ S ] with State[ S ] /* with Mutable[ S ] */ {
   protected def sources: StateSources[ S ]

//      protected def connect()( implicit tx: S#Tx ) : Unit
//      // note: 'undeploy' is a rather horrible neologism (only used by Apache Tomcat and with airbags...)
//      protected def disconnect()( implicit tx: S#Tx ) : Unit

   final def addReactor( r: StateReactor[ S ])( implicit tx: S#Tx ) {
      val old = children.get
      children.set( old :+ r )
      if( old.isEmpty ) {
//            connector.connect()
         sources.stateSources.foreach( _.addReactor( this ))
      }
   }

   final def removeReactor( r: StateReactor[ S ])( implicit tx: S#Tx ) {
      val xs = children.get
      val i = xs.indexOf( r )
      if( i >= 0 ) {
         val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
         children.set( xs1 )
         if( xs1.isEmpty ) {
//               connector.disconnect()
            sources.stateSources.foreach( _.removeReactor( this ))
         }
      }
   }
}

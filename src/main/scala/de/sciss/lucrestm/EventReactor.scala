/*
 *  EventReactor.scala
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

object EventReactor {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, EventReactor[ S ]] = new Ser[ S ]

   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, EventReactor[ S ]] {
      def write( r: EventReactor[ S ], out: DataOutput ) { r.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : EventReactor[ S ] = {
         if( in.readUnsignedByte() == 0 ) {
            new EventReactorBranchStub[ S ]( in, tx, access )
         } else {
            new EventReactorLeaf[ S ]( in.readInt() )
         }
      }
   }

   private final class EventReactorBranchStub[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc )
   extends EventReactorBranchLike[ S ] {
      val id = tx0.readID( in, acc )
      protected val children = tx0.readVar[ IIdxSeq[ EventReactor[ S ]]]( id, in )
   }
}

sealed trait EventReactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
   def propagate( key: Int )( implicit tx: S#Tx ) : Unit
}

//object EventReactorLeaf {
//   def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : EventReactorLeaf[ S ] = new EventReactorLeaf( map.newID() )
//}

final case class EventReactorLeaf[ S <: Sys[ S ]] private[lucrestm]( id: Int ) extends EventReactor[ S ] {
   def write( out: DataOutput ) {
      out.writeUnsignedByte( 1 )
      out.writeInt( id )
   }

   def propagate( key: Int )( implicit tx: S#Tx ) {
      tx.invokeEventReaction( this, key )
   }

   def dispose()( implicit tx: S#Tx ) {
      tx.removeEventReaction( this )
   }
}

object Event {
   def constant[ S <: Sys[ S ]] : Event[ S ] = new Const[ S ]

   private final class Const[ S <: Sys[ S ]] extends Event[ S ] {
      def addReactor(    r: EventReactor[ S ])( implicit tx: S#Tx ) {}
      def removeReactor( r: EventReactor[ S ])( implicit tx: S#Tx ) {}
   }
}

trait Event[ S <: Sys[ S ]] {
   def addReactor(    r: EventReactor[ S ])( implicit tx: S#Tx ) : Unit
   def removeReactor( r: EventReactor[ S ])( implicit tx: S#Tx ) : Unit
}

object EventSources {
   def none[ S <: Sys[ S ]] : EventSources[ S ] = new NoSources[ S ]

   private final class NoSources[ S <: Sys[ S ]] extends EventSources[ S ] {
      def eventSources( implicit tx: S#Tx ) : IIdxSeq[ Event[ S ]] = IIdxSeq.empty
   }
}

trait EventSources[ S <: Sys[ S ]] {
   def eventSources( implicit tx: S#Tx ) : IIdxSeq[ Event[ S ]]
}

object EventReactorBranch {
   def apply[ S <: Sys[ S ]]( sources: EventSources[ S ])( implicit tx: S#Tx ) : EventReactorBranch[ S ] =
      new New[ S ]( sources, tx )

   private final class New[ S <: Sys[ S ]]( protected val sources: EventSources[ S ], tx0: S#Tx )
   extends EventReactorBranch[ S ] {
      val id = tx0.newID()
      protected val children = tx0.newVar[ IIdxSeq[ EventReactor[ S ]]]( id, IIdxSeq.empty )
   }

//      def serializer[ S <: Sys[ S ]]: TxnSerializer[ S#Tx, S#Acc, EventReactorBranch[ S ]] =
//         new TxnSerializer[ S#Tx, S#Acc, EventReactorBranch[ S ]] {
//            def write( r: EventReactorBranch[ S ], out: DataOutput ) { r.write( out )}
//            def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : EventReactorBranch[ S ] = {
//               require( in.readUnsignedByte() == 0 )
//               new ReactorBranchRead( in, access, tx )
//            }
//         }

   def read[ S <: Sys[ S ]]( sources: EventSources[ S ], in: DataInput, access: S#Acc )
                           ( implicit tx: S#Tx ) : EventReactorBranch[ S ] = {
      require( in.readUnsignedByte() == 0 )
      new Read[ S ]( sources, in, access, tx )
   }

   private final class Read[ S <: Sys[ S ]]( protected val sources: EventSources[ S ],
                                             in: DataInput, access: S#Acc, tx0: S#Tx )
   extends EventReactorBranch[ S ] {
      val id = tx0.readID( in, access )
      protected val children = tx0.readVar[ IIdxSeq[ EventReactor[ S ]]]( id, in )
   }
}

sealed trait EventReactorBranchLike[ S <: Sys[ S ]] extends EventReactor[ S ] {
   def id: S#ID
   protected def children: S#Var[ IIdxSeq[ EventReactor[ S ]]]

   final def isConnected( implicit tx: S#Tx ) : Boolean = children.get.nonEmpty

   final def propagate( key: Int )( implicit tx: S#Tx ) {
      children.get.foreach( _.propagate( key ))
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

   override def toString = "EventReactorBranch" + id

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ EventReactorBranchLike[ _ ]]) {
         id == that.asInstanceOf[ EventReactorBranchLike[ _ ]].id
      } else super.equals( that ))
   }

   override def hashCode = id.hashCode()
}

/**
 * A `EventReactorBranch` is most similar to EScala's `EventNode` class. It represents an observable
 * object and can also act as an observer itself.
 */
sealed trait EventReactorBranch[ S <: Sys[ S ]] extends EventReactorBranchLike[ S ] with Event[ S ] /* with Mutable[ S ] */ {
   protected def sources: EventSources[ S ]

//      protected def connect()( implicit tx: S#Tx ) : Unit
//      // note: 'undeploy' is a rather horrible neologism (only used by Apache Tomcat and with airbags...)
//      protected def disconnect()( implicit tx: S#Tx ) : Unit

   final def addReactor( r: EventReactor[ S ])( implicit tx: S#Tx ) {
      val old = children.get
      children.set( old :+ r )
      if( old.isEmpty ) {
//            connector.connect()
         sources.stateSources.foreach( _.addReactor( this ))
      }
   }

   final def removeReactor( r: EventReactor[ S ])( implicit tx: S#Tx ) {
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

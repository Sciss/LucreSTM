/*
 *  Reactor.scala
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

object Reactor {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] = new Ser[ S ]

   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] {
      def write( r: Reactor[ S ], out: DataOutput ) { r.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
         if( in.readUnsignedByte() == 0 ) {
            new ReactorBranchStubRead[ S ]( in, tx, access )
         } else {
            new ReactorLeaf[ S ]( in.readLong() )
         }
      }
   }

   private final class ReactorBranchStubRead[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc )
   extends ReactorBranchStub[ S ] {
      val id = tx0.readID( in, acc )
      protected val children = tx0.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
   }
}

sealed trait Reactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
   def propagate()( implicit tx: S#Tx ) : Unit
}

//object ReactorLeaf {
//   def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : ReactorLeaf[ S ] = new ReactorLeaf( map.newID() )
//}

final case class ReactorLeaf[ S <: Sys[ S ]] private[lucrestm]( id: Long ) extends Reactor[ S ] {
   def write( out: DataOutput ) {
      out.writeUnsignedByte( 1 )
      out.writeLong( id )
   }

   def propagate()( implicit tx: S#Tx ) {
      sys.error( "TODO" )
//      map.invoke( this )
   }

   def dispose()( implicit tx: S#Tx ) {
      tx.removeReaction( this )
   }
}

object Observable {
   def deaf[ S <: Sys[ S ]] : Observable[ S ] = new Deaf[ S ]

   private final class Deaf[ S <: Sys[ S ]] extends Observable[ S ] {
      def addReactor(    r: Reactor[ S ])( implicit tx: S#Tx ) {}
      def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {}
   }
}

trait Observable[ S <: Sys[ S ]] {
   def addReactor(    r: Reactor[ S ])( implicit tx: S#Tx ) : Unit
   def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Unit
}

trait ReactorSources[ S <: Sys[ S ]] {
   def reactorSources( implicit tx: S#Tx ) : IIdxSeq[ Observable[ S ]]
}

object ReactorBranch {
   def apply[ S <: Sys[ S ]]( sources: ReactorSources[ S ])( implicit tx: S#Tx ) : ReactorBranch[ S ] =
      new New[ S ]( sources, tx )

   private final class New[ S <: Sys[ S ]]( protected val sources: ReactorSources[ S ], tx0: S#Tx )
   extends ReactorBranch[ S ] {
      val id = tx0.newID()
      protected val children = tx0.newVar[ IIdxSeq[ Reactor[ S ]]]( id, IIdxSeq.empty )
   }

//      def serializer[ S <: Sys[ S ]]: TxnSerializer[ S#Tx, S#Acc, ReactorBranch[ S ]] =
//         new TxnSerializer[ S#Tx, S#Acc, ReactorBranch[ S ]] {
//            def write( r: ReactorBranch[ S ], out: DataOutput ) { r.write( out )}
//            def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : ReactorBranch[ S ] = {
//               require( in.readUnsignedByte() == 0 )
//               new ReactorBranchRead( in, access, tx )
//            }
//         }

   def read[ S <: Sys[ S ]]( sources: ReactorSources[ S ], in: DataInput, access: S#Acc )
                           ( implicit tx: S#Tx ) : ReactorBranch[ S ] = {
      require( in.readUnsignedByte() == 0 )
      new Read[ S ]( sources, in, access, tx )
   }

   private final class Read[ S <: Sys[ S ]]( protected val sources: ReactorSources[ S ],
                                             in: DataInput, access: S#Acc, tx0: S#Tx )
   extends ReactorBranch[ S ] {
      val id = tx0.readID( in, access )
      protected val children = tx0.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
   }
}

sealed trait ReactorBranchStub[ S <: Sys[ S ]] extends Reactor[ S ] {
   def id: S#ID
   protected def children: S#Var[ IIdxSeq[ Reactor[ S ]]]

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

   override def toString = "ReactorBranch" + id

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ ReactorBranchStub[ _ ]]) {
         id == that.asInstanceOf[ ReactorBranchStub[ _ ]].id
      } else super.equals( that ))
   }

   override def hashCode = id.hashCode()
}

/**
 * A `ReactorBranch` is most similar to EScala's `EventNode` class. It represents an observable
 * object and can also act as an observer itself.
 */
sealed trait ReactorBranch[ S <: Sys[ S ]] extends ReactorBranchStub[ S ] with Observable[ S ] /* with Mutable[ S ] */ {
   protected def sources: ReactorSources[ S ]

//      protected def connect()( implicit tx: S#Tx ) : Unit
//      // note: 'undeploy' is a rather horrible neologism (only used by Apache Tomcat and with airbags...)
//      protected def disconnect()( implicit tx: S#Tx ) : Unit

   final def addReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
      val old = children.get
      children.set( old :+ r )
      if( old.isEmpty ) {
//            connector.connect()
         sources.reactorSources.foreach( _.addReactor( this ))
      }
   }

   final def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
      val xs = children.get
      val i = xs.indexOf( r )
      if( i >= 0 ) {
         val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
         children.set( xs1 )
         if( xs1.isEmpty ) {
//               connector.disconnect()
            sources.reactorSources.foreach( _.removeReactor( this ))
         }
      }
   }
}
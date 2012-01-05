/*
 *  Event.scala
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

import collection.immutable.{IndexedSeq => IIdxSeq}
import annotation.switch

object Event {
   type Reaction  = () => () => Unit
   type Reactions = IIdxSeq[ Reaction ]

   /**
    * A trait to serialize events which can be both constants and nodes.
    * An implementation mixing in this trait just needs to implement methods
    * `readConstant` to return the constant instance, and `read` with the
    * `EventTargets` argument to return the node instance.
    */
   trait Serializer[ S <: Sys[ S ], Repr <: Event[ S, _ ]]
   extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
      final def write( v: Repr, out: DataOutput ) { v.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
         (in.readUnsignedByte(): @switch) match {
            case 2 => readConstant( in )
            case 0 =>
               val targets = Targets.read[ S ]( in, access )
               read( in, access, targets )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
      }

      def readConstant( in: DataInput )( implicit tx: S#Tx ) : Repr
   }


   trait Observable[ S <: Sys[ S ], /* @specialized SUCKAZZZ */ A, Repr ] {
      def observe( fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ]
   }

   object Observer {
      def apply[ S <: Sys[ S ], /* @specialized SUCKAZZZ */ A, Repr <: Event[ S, A ]](
         reader: Reader[ S, Repr ], fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] = {

//         val key = tx.addEventReaction[ A, Repr ]( reader, fun )
//         new Impl[ S, A, Repr ]( key )
         sys.error( "TODO" )
      }

      private final class Impl[ S <: Sys[ S ], /* @specialized SUCKAZZZ */ A, Repr <: Event[ S, A ]](
         key: ReactorKey[ S ])
      extends Observer[ S, A, Repr ] {
         override def toString = "EventObserver<" + key.key + ">"

         def add( event: Repr )( implicit tx: S#Tx ) {
            event.addReactor( key )
         }

         def remove( event: Repr )( implicit tx: S#Tx ) {
            event.removeReactor( key )
         }

         def dispose()( implicit tx: S#Tx ) {
//            tx.removeEventReaction( key )
            sys.error( "TODO" )
         }
      }
   }
   sealed trait Observer[ S <: Sys[ S ], /* @specialized SUCKAZZZ */ A, -Repr ] extends Disposable[ S#Tx ] {
      def add(    event: Repr )( implicit tx: S#Tx ) : Unit
      def remove( event: Repr )( implicit tx: S#Tx ) : Unit
   }

   object Reader {
      def unsupported[ S <: Sys[ S ], Repr ] : Reader[ S, Repr ] = new Unsupported[ S, Repr ]

      private final class Unsupported[ S <: Sys[ S ], Repr ] extends Reader[ S, Repr ] {
         def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Repr =
            throw new UnsupportedOperationException()
      }
   }

   trait Reader[ S <: Sys[ S ], +Repr ] {
      def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Repr
   }

   object Targets {
     def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
        val id         = tx.newID()
        val children   = tx.newVar[ IIdxSeq[ Reactor[ S ]]]( id, IIdxSeq.empty )
        new Impl( id, children )
     }

     def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
        val id            = tx.readID( in, access )
        val children      = tx.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
        new Impl[ S ]( id, children )
     }

     private[lucrestm] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ IIdxSeq[ Reactor[ S ]]]) : Targets[ S ] =
        new Impl( id, children )

     private final class Impl[ S <: Sys[ S ]](
        private[lucrestm] val id: S#ID, children: S#Var[ IIdxSeq[ Reactor[ S ]]])
     extends Targets[ S ] {
        override def toString = "EventTargets" + id

        private[lucrestm] def propagate( event: Event[ S, _ ], reactions: Reactions )
                                       ( implicit tx: S#Tx ) : Reactions = {
           children.get.foldLeft( reactions )( (rs, r) => r.propagate( event, rs ))
        }

        private[lucrestm] def addReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean = {
           val old = children.get
           children.set( old :+ r )
           old.isEmpty
        }

        private[lucrestm] def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean = {
           val xs = children.get
           val i = xs.indexOf( r )
           if( i >= 0 ) {
              val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
              children.set( xs1 )
              xs1.isEmpty
           } else false
        }

        def write( out: DataOutput ) {
           out.writeUnsignedByte( 0 )
           id.write( out )
           children.write( out )
        }

        private[lucrestm] def isConnected( implicit tx: S#Tx ) : Boolean = children.get.nonEmpty

        def dispose()( implicit tx: S#Tx ) {
           require( !isConnected, "Disposing a event reactor which is still being observed" )
           id.dispose()
           children.dispose()
        }
     }
  }

   sealed trait Targets[ S <: Sys[ S ]] extends Reactor[ S ] {
     private[lucrestm] def id: S#ID
     private[lucrestm] def addReactor(    r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean
     private[lucrestm] def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean
     private[lucrestm] def isConnected( implicit tx: S#Tx ) : Boolean
  }

   type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _ ]]

   def noSources[ S <: Sys[ S ]] : Sources[ S ] = IIdxSeq.empty

   /**
    * A `EventNode` is most similar to EScala's `EventNode` class. It represents an observable
    * object and can also act as an observer itself.
    */
   trait Node[ S <: Sys[ S ], /* @specialized SUCKAZZZ */ A ] extends Reactor[ S ] with Event[ S, A ] {
      protected def eventSources( implicit tx: S#Tx ) : Sources[ S ]
      protected def targets: Targets[ S ]
      protected def writeData( out: DataOutput ) : Unit
      protected def disposeData()( implicit tx: S#Tx ) : Unit

      final def id: S#ID = targets.id

      final private[lucrestm] def propagate( parent: Event[ S, _ ], reactions: Reactions )
                                                ( implicit tx: S#Tx ) : Reactions =
         targets.propagate( this, reactions ) // parent event not important

      final def write( out: DataOutput ) {
         targets.write( out )
         writeData( out )
      }

      final def dispose()( implicit tx: S#Tx ) {
         targets.dispose()
         disposeData()
      }

      final private[lucrestm] def addReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
         if( targets.addReactor( r )) {
            eventSources.foreach( _.addReactor( this ))
         }
      }

      final private[lucrestm] def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
         if( targets.removeReactor( r )) {
            eventSources.foreach( _.removeReactor( this ))
         }
      }

      override def toString = "EventNode" + id

      override def equals( that: Any ) : Boolean = {
         (if( that.isInstanceOf[ Node[ _, _ ]]) {
            id == that.asInstanceOf[ Node[ _, _ ]].id
         } else super.equals( that ))
      }

      override def hashCode = id.hashCode()
   }

   object Reactor {
      implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] = new Ser[ S ]

      private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] {
         override def toString = "Event.Reactor.Serializer"

         def write( r: Reactor[ S ], out: DataOutput ) { r.write( out )}

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
            (in.readUnsignedByte(): @switch) match {
               case 0 =>
                  val id            = tx.readID( in, access )
                  val children      = tx.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
                  val targets       = Targets[ S ]( id, children )
                  val observerKeys  = children.get.collect {
                     case ReactorKey( key ) => key
                  }
//                  tx.mapEventTargets( in, access, targets, observerKeys )
                  sys.error( "TODO" )
               case 1 =>
                  val key  = in.readInt()
                  new ReactorKey[ S ]( key )

               case cookie => sys.error( "Unexpected cookie " + cookie )
            }
         }
      }
   }

   sealed trait Reactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
      private[lucrestm] def propagate( event: Event[ S, _ ], reactions: Reactions )
                                     ( implicit tx: S#Tx ) : Reactions
   }

   final case class ReactorKey[ S <: Sys[ S ]] private[lucrestm] ( key: Int ) extends Reactor[ S ] {
      private[lucrestm] def propagate( event: Event[ S, _ ], reactions: Reactions )
                                     ( implicit tx: S#Tx ) : Reactions = {
//         tx.propagateEvent( key, event, reactions )
         sys.error( "TODO" )
      }

      def dispose()( implicit tx: S#Tx ) {}

      def write( out: DataOutput ) {
         out.writeUnsignedByte( 1 )
         out.writeInt( key )
      }
   }
}

/**
 * `Event` is not sealed in order to allow you define traits inheriting from it, while the concrete
 * implementations will still most likely extends `EventConstant` or `EventNode`.
 */
trait Event[ S <: Sys[ S ], Upd ] extends Writer {
   private[lucrestm] def addReactor(     r: Event.Reactor[ S ])( implicit tx: S#Tx ) : Unit
   private[lucrestm] def removeReactor(  r: Event.Reactor[ S ])( implicit tx: S#Tx ) : Unit
}
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

   trait Observable[ S <: Sys[ S ], A, Repr ] {
      def observe( fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ]
   }

   sealed trait Reader[ S <: Sys[ S ], +Repr, T ] {
      def read( in: DataInput, access: S#Acc, targets: T )( implicit tx: S#Tx ) : Repr
   }

   /**
    * A trait to serialize events which can be both constants and immutable nodes.
    * An implementation mixing in this trait just needs to implement methods
    * `readConstant` to return the constant instance, and `read` with the
    * `Event.Immutable.Targets` argument to return the immutable node instance.
    */
   trait Serializer[ S <: Sys[ S ], Repr <: Event[ S, _ ]]
   extends Immutable.Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
      final def write( v: Repr, out: DataOutput ) { v.write( out )}

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
         (in.readUnsignedByte(): @switch) match {
            case 3 => readConstant( in )
            case 0 =>
               val targets = Immutable.Targets.read[ S ]( in, access )
               read( in, access, targets )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
      }

      def readConstant( in: DataInput )( implicit tx: S#Tx ) : Repr
   }

   object Observer {
      def apply[ S <: Sys[ S ], A, Repr <: Event[ S, A ]](
         reader: Reader[ S, Repr, _ ], fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] = {

         val key = tx.addEventReaction[ A, Repr ]( reader, fun )
         new Impl[ S, A, Repr ]( key )
      }

      private final class Impl[ S <: Sys[ S ], A, Repr <: Event[ S, A ]](
         key: ReactorKey[ S ])
      extends Observer[ S, A, Repr ] {
         override def toString = "Event.Observer<" + key.key + ">"

         def add( event: Repr )( implicit tx: S#Tx ) {
            event.addReactor( key )
         }

         def remove( event: Repr )( implicit tx: S#Tx ) {
            event.removeReactor( key )
         }

         def dispose()( implicit tx: S#Tx ) {
            tx.removeEventReaction( key )
         }
      }
   }
   sealed trait Observer[ S <: Sys[ S ], A, -Repr ] extends Disposable[ S#Tx ] {
      def add(    event: Repr )( implicit tx: S#Tx ) : Unit
      def remove( event: Repr )( implicit tx: S#Tx ) : Unit
   }

   sealed trait Targets[ S <: Sys[ S ]] extends Reactor[ S ] {
      private[lucrestm] def id: S#ID
//      private[lucrestm] def addReactor(    r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean
//      private[lucrestm] def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean
//      private[lucrestm] def isConnected( implicit tx: S#Tx ) : Boolean

      protected def children: S#Var[ IIdxSeq[ Reactor[ S ]]]

      override def toString = "Event.Targets" + id

      final private[lucrestm] def propagate( source: Posted[ S, _ ], parent: Event[ S, _ ], reactions: Reactions )
                                     ( implicit tx: S#Tx ) : Reactions = {
         children.get.foldLeft( reactions )( (rs, r) => r.propagate( source, parent, rs ))
      }

      final private[lucrestm] def addReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean = {
         val old = children.get
         children.set( old :+ r )
         old.isEmpty
      }

      final private[lucrestm] def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Boolean = {
         val xs = children.get
         val i = xs.indexOf( r )
         if( i >= 0 ) {
            val xs1 = xs.patch( i, IIdxSeq.empty, 1 ) // XXX crappy way of removing a single element
            children.set( xs1 )
            xs1.isEmpty
         } else false
      }

      final private[lucrestm] def isConnected( implicit tx: S#Tx ) : Boolean = children.get.nonEmpty
   }

   type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _ ]]

   def noSources[ S <: Sys[ S ]] : Sources[ S ] = IIdxSeq.empty

   /**
    * An `Event.Node` is most similar to EScala's `EventNode` class. It represents an observable
    * object and can also act as an observer itself.
    */
   sealed trait Node[ S <: Sys[ S ], A ] extends Reactor[ S ] with Event[ S, A ] {
      protected def sources( implicit tx: S#Tx ) : Sources[ S ]
      protected def targets: Targets[ S ]
      protected def writeData( out: DataOutput ) : Unit
      protected def disposeData()( implicit tx: S#Tx ) : Unit

      final def id: S#ID = targets.id

      final private[lucrestm] def propagate( source: Posted[ S, _ ], parent: Event[ S, _ ], reactions: Reactions )
                                           ( implicit tx: S#Tx ) : Reactions =
         targets.propagate( source, this, reactions ) // parent event not important

      final def write( out: DataOutput ) {
         targets.write( out )
         writeData( out )
      }

      final def dispose()( implicit tx: S#Tx ) {
         targets.dispose()
         disposeData()
      }

      override def equals( that: Any ) : Boolean = {
         (if( that.isInstanceOf[ Node[ _, _ ]]) {
            id == that.asInstanceOf[ Node[ _, _ ]].id
         } else super.equals( that ))
      }

      override def hashCode = id.hashCode()
   }

   object Immutable {
      trait Observable[ S <: Sys[ S ], A, Repr <: Event[ S, A ]]
      extends Immutable[ S, A ] with Event.Observable[ S, A, Repr ] {
         me: Repr =>

         protected def reader : Reader[ S, Repr ]
         final def observe( fun: (S#Tx, A) => Unit )( implicit tx: S#Tx ) : Observer[ S, A, Repr ] = {
            val res = Observer[ S, A, Repr ]( reader, fun )
            res.add( this )
            res
         }
      }

      object Targets {
         def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
            val id         = tx.newID()
            val children   = tx.newVar[ IIdxSeq[ Reactor[ S ]]]( id, IIdxSeq.empty )
            new Impl( id, children )
         }

         private[lucrestm] def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
            val id            = tx.readID( in, access )
            val children      = tx.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
            new Impl[ S ]( id, children )
         }

         private[lucrestm] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ IIdxSeq[ Reactor[ S ]]]) : Targets[ S ] =
            new Impl( id, children )

         private final class Impl[ S <: Sys[ S ]](
            private[lucrestm] val id: S#ID, protected val children: S#Var[ IIdxSeq[ Reactor[ S ]]])
         extends Targets[ S ] {
            def write( out: DataOutput ) {
               out.writeUnsignedByte( 0 )
               id.write( out )
               children.write( out )
            }

            def dispose()( implicit tx: S#Tx ) {
               require( !isConnected, "Disposing a event reactor which is still being observed" )
               id.dispose()
               children.dispose()
            }
         }
      }

      sealed trait Targets[ S <: Sys[ S ]] extends Event.Targets[ S ]

      trait Reader[ S <: Sys[ S ], +Repr ] extends Event.Reader[ S, Repr, Targets[ S ]] {
//         def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Repr
      }

      /**
       * A trait to serialize events which are immutable nodes.
       * An implementation mixing in this trait just needs to implement
       * `read` with the `Event.Immutable.Targets` argument to return the node instance.
       */
      trait Serializer[ S <: Sys[ S ], Repr <: Immutable[ S, _ ]]
      extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
         final def write( v: Repr, out: DataOutput ) { v.write( out )}

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
            val cookie = in.readUnsignedByte()
            if( cookie == 0 ) {
               val targets = Targets.read[ S ]( in, access )
               read( in, access, targets )
            } else {
               sys.error( "Unexpected cookie " + cookie )
            }
         }
      }
   }

   trait Immutable[ S <: Sys[ S ], A ] extends Node[ S, A ] {
      protected def targets: Immutable.Targets[ S ]

      override def toString = "Event.Immutable" + id

      final private[lucrestm] def addReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
         if( targets.addReactor( r )) {
            sources.foreach( _.addReactor( this ))
         }
      }

      final private[lucrestm] def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
         if( targets.removeReactor( r )) {
            sources.foreach( _.removeReactor( this ))
         }
      }

//      def pull( source: Event.Posted[ S, _ ])( implicit tx: S#Tx ) : Option[ A ] = {
//         if( source.source == this ) Some( source.update.asInstanceOf[ A ]) else None
//      }

//      protected def value( source: Event.Posted[ S, _ ])( implicit tx: S#Tx ) : Option[ A ]
   }

   trait Source[ S <: Sys[ S ], A ] extends Node[ S, A ] {
      protected def fire( update: A )( implicit tx: S#Tx ) {
         val posted     = Event.Posted( this, update )
         val reactions  = propagate( posted, this, IIdxSeq.empty )
         reactions.map( _.apply() ).foreach( _.apply() )
      }
   }

//   object Constant {
////      def apply
//   }

   trait Val[ S <: Sys[ S ], A ] extends Event[ S, Change[ A ]] {
      def value( implicit tx: S#Tx ) : A
   }

   trait Root[ S <: Sys[ S ], A ] {
      final protected def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq.empty

      final def pull( source: Posted[ S, _ ])( implicit tx: S#Tx ) : Option[ A ] = {
         if( source.source == this ) Some( source.update.asInstanceOf[ A ]) else None
      }
   }

   final case class Change[ @specialized A ]( before: A, now: A )

   trait Constant[ S <: Sys[ S ], A ] extends Val[ S, A ] with Root[ S, Change[ A ]] {
      protected def constValue : A
      final def value( implicit tx: S#Tx ) : A = constValue
      final private[lucrestm] def addReactor(     r: Reactor[ S ])( implicit tx: S#Tx ) {}
      final private[lucrestm] def removeReactor(  r: Reactor[ S ])( implicit tx: S#Tx ) {}

      final def write( out: DataOutput ) {
         out.writeUnsignedByte( 3 )
         writeData( out )
      }

      protected def writeData( out: DataOutput ) : Unit
   }

   trait Singleton[ S <: Sys[ S ]] {
      final protected def disposeData()( implicit tx: S#Tx ) {}
      final protected def writeData( out: DataOutput ) {}
   }

   trait Trigger[ S <: Sys[ S ], A ] extends Source[ S, A ] with Root[ S, A ] with Immutable[ S, A ] {
      override def toString = "Event.Trigger" + id

      final override def fire( update: A )( implicit tx: S#Tx ) { super.fire( update )}
   }

   object Bang {
      private type Obs[ S <: Sys[ S ]] = Bang[ S ] with Immutable.Observable[ S, Unit, Bang[ S ]]

      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : Obs[ S ] = new ObsImpl[ S ] {
            protected val targets = Immutable.Targets[ S ]
         }

      private sealed trait ObsImpl[ S <: Sys[ S ]] extends Bang[ S ] with Immutable.Observable[ S, Unit, Bang[ S ]] {
         protected def reader = serializer[ S ]
      }

      def serializer[ S <: Sys[ S ]] : Immutable.Serializer[ S, Obs[ S ]] = new Immutable.Serializer[ S, Obs[ S ]] {
         def read( in: DataInput, access: S#Acc, _targets: Immutable.Targets[ S ])( implicit tx: S#Tx ) : Obs[ S ] =
            new ObsImpl[ S ] {
               protected val targets = _targets
            }
      }
   }
   trait Bang[ S <: Sys[ S ]] extends Trigger[ S, Unit ] with Singleton[ S ] {
      def fire()( implicit tx: S#Tx ) { fire( () )}
   }

   object Mutable {
      object Targets {
         def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Targets[ S ] = {
            val id         = tx.newID()
            val children   = tx.newVar[ IIdxSeq[ Reactor[ S ]]]( id, IIdxSeq.empty )
            val invalid    = tx.newBooleanVar( id, false )
            new Impl( id, children, invalid )
         }

         private[lucrestm] def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Targets[ S ] = {
            val id            = tx.readID( in, access )
            val children      = tx.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
            val invalid       = tx.readBooleanVar( id, in )
            new Impl[ S ]( id, children, invalid )
         }

         private[lucrestm] def apply[ S <: Sys[ S ]]( id: S#ID, children: S#Var[ IIdxSeq[ Reactor[ S ]]],
                                                      invalid: S#Var[ Boolean ]) : Targets[ S ] =
            new Impl( id, children, invalid )

         private final class Impl[ S <: Sys[ S ]](
            private[lucrestm] val id: S#ID, protected val children: S#Var[ IIdxSeq[ Reactor[ S ]]], invalid: S#Var[ Boolean ])
         extends Targets[ S ] {
            def isInvalid( implicit tx: S#Tx ) : Boolean = invalid.get
            def validated()( implicit tx: S#Tx ) { invalid.set( false )}

            def write( out: DataOutput ) {
               out.writeUnsignedByte( 1 )
               id.write( out )
               children.write( out )
               invalid.write( out )
            }

            def dispose()( implicit tx: S#Tx ) {
               require( !isConnected, "Disposing a event reactor which is still being observed" )
               id.dispose()
               children.dispose()
               invalid.dispose()
            }
         }
      }

      sealed trait Targets[ S <: Sys[ S ]] extends Event.Targets[ S ] {
         private[lucrestm] def isInvalid( implicit tx: S#Tx ) : Boolean
         def validated()( implicit tx: S#Tx ) : Unit
      }

      trait Reader[ S <: Sys[ S ], +Repr ] extends Event.Reader[ S, Repr, Targets[ S ]] {
//         def read( in: DataInput, access: S#Acc, targets: Targets[ S ] /*, revalidate: Boolean */)( implicit tx: S#Tx ) : Repr
      }

      /**
       * A trait to serialize events which are mutable nodes.
       * An implementation mixing in this trait just needs to implement
       * `read` with the `Event.Mutable.Targets` argument to return the node instance.
       */
      trait Serializer[ S <: Sys[ S ], Repr <: Mutable[ S, _ ]]
      extends Reader[ S, Repr ] with TxnSerializer[ S#Tx, S#Acc, Repr ] {
         final def write( v: Repr, out: DataOutput ) { v.write( out )}

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
            val cookie = in.readUnsignedByte()
            if( cookie == 1 ) {
               val targets = Targets.read[ S ]( in, access )
               val invalid = targets.isInvalid
               val res     = read( in, access, targets /*, invalid */)
               if( invalid ) require( !targets.isInvalid, "Reader did not validate structure" )
               res
            } else {
               sys.error( "Unexpected cookie " + cookie )
            }
         }
      }
   }

//   abstract class Mutable[ S <: Sys[ S ], A ]( tx0: S#Tx ) extends Node[ S, A ]
   trait Mutable[ S <: Sys[ S ], A ] extends Node[ S, A ] {
      protected def targets: Mutable.Targets[ S ]

      override def toString = "Event.Mutable" + id
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
                  val targets       = Immutable.Targets[ S ]( id, children )
                  val observerKeys  = children.get.collect {
                     case ReactorKey( key ) => key
                  }
                  tx.mapEventTargets( in, access, targets, observerKeys )
               case 1 =>
                  val id            = tx.readID( in, access )
                  val children      = tx.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
                  val invalid       = tx.readBooleanVar( id, in )
                  val targets       = Mutable.Targets[ S ]( id, children, invalid )
                  val observerKeys  = children.get.collect {
                     case ReactorKey( key ) => key
                  }
                  tx.mapEventTargets( in, access, targets, observerKeys )
               case 2 =>
                  val key  = in.readInt()
                  new ReactorKey[ S ]( key )

               case cookie => sys.error( "Unexpected cookie " + cookie )
            }
         }
      }
   }

   final case class Posted[ S <: Sys[ S ], A ] private[lucrestm] ( source: Event[ S, A ], update: A )

   sealed trait Reactor[ S <: Sys[ S ]] extends Writer with Disposable[ S#Tx ] {
      private[lucrestm] def propagate( source: Posted[ S, _ ], parent: Event[ S, _ ], reactions: Reactions )
                                     ( implicit tx: S#Tx ) : Reactions
   }

   final case class ReactorKey[ S <: Sys[ S ]] private[lucrestm] ( key: Int ) extends Reactor[ S ] {
      private[lucrestm] def propagate( source: Posted[ S, _ ], parent: Event[ S, _ ], reactions: Reactions )
                                     ( implicit tx: S#Tx ) : Reactions = {
         tx.propagateEvent( key, source, parent, reactions )
      }

      def dispose()( implicit tx: S#Tx ) {}

      def write( out: DataOutput ) {
         out.writeUnsignedByte( 2 )
         out.writeInt( key )
      }
   }
}

/**
 * `Event` is not sealed in order to allow you define traits inheriting from it, while the concrete
 * implementations will still most likely extends `EventConstant` or `EventNode`.
 */
trait Event[ S <: Sys[ S ], A ] extends Writer {
   private[lucrestm] def addReactor(     r: Event.Reactor[ S ])( implicit tx: S#Tx ) : Unit
   private[lucrestm] def removeReactor(  r: Event.Reactor[ S ])( implicit tx: S#Tx ) : Unit

   def pull( source: Event.Posted[ S, _ ])( implicit tx: S#Tx ) : Option[ A ]
}
package de.sciss.lucrestm
package fluent

import collection.immutable.{IndexedSeq => IIdxSeq}
import concurrent.stm.{TMap, Ref => STMRef}

object ReferenceTest extends App {
   val system = Confluent()

   type Tx = Confluent#Tx

   object Sink {
      implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Sink[ S ]] = new Ser[ S ]

      private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Sink[ S ]] {
         def write( sink: Sink[ S ], out: DataOutput ) { sink.write( out )}

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Sink[ S ] = {
            if( in.readUnsignedByte() == 0 ) {
               new EventRead[ S ]( in, tx, access )
            } else {
               new ReactionKey[ S ]( in.readInt() )
            }
         }
      }

      private final class EventRead[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc ) extends Event[ S ] {
         val id = tx0.readID( in, acc )
         protected val sinks = tx0.readVar[ IIdxSeq[ Sink[ S ]]]( id, in )
      }
   }

   object Event {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : Event[ S ] = new EventNew[ S ]( tx )

      private final class EventNew[ S <: Sys[ S ]]( tx0: S#Tx ) extends Event[ S ] {
         val id = tx0.newID()
         protected val sinks = tx0.newVar[ IIdxSeq[ Sink[ S ]]]( id, IIdxSeq.empty )
      }
   }

   sealed trait Sink[ S <: Sys[ S ]] extends Writer {
      def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit
   }

   object ReactionKey {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx, map: LiveMap[ S ]) : ReactionKey[ S ] = new ReactionKey( map.newID() )
   }

   final case class ReactionKey[ S <: Sys[ S ]]( id: Int ) extends Sink[ S ] {
      def write( out: DataOutput ) {
         out.writeUnsignedByte( 1 )
         out.writeInt( id )
      }

      def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) {
         map.invoke( this )
      }
   }

   sealed trait Event[ S <: Sys[ S ]] extends Sink[ S ] with Mutable[ S ] {
      final def addPropagator( sink: Sink[ S ])( implicit tx: S#Tx ) {
         sinks.transform( _ :+ sink )
//         val xs = props.get
//         props.set( xs :+ sink )
//         if( xs.isEmpty /* && reactions.get.isEmpty */) {
//            deploy()
//         }
      }

      final def removePropagator( sink: Sink[ S ])( implicit tx: S#Tx ) {
         val xs = sinks.get
         val i = xs.indexOf( sink )
         if( i >= 0 ) {
            val xs1 = xs.patch( i, IIdxSeq.empty, 1 )
            sinks.set( xs1 )
//            if( xs1.isEmpty /* && reactions.get.isEmpty */) undeploy()
         }
      }

      final def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) {
//         map.propagate( this )
         sinks.get.foreach( _.propagate() )
      }

      final protected def writeData( out: DataOutput ) {
         out.writeUnsignedByte( 0 )
         sinks.write( out )
      }

      final protected def disposeData()( implicit tx: S#Tx ) {
         sinks.dispose()
      }

      protected def sinks: S#Var[ IIdxSeq[ Sink[ S ]]]
   }

   object LiveMap {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : LiveMap[ S ] = new Impl[ S ]( tx )

      private final class Impl[ S <: Sys[ S ]]( tx0: S#Tx )extends LiveMap[ S ] {
         private val map   = TMap.empty[ ReactionKey[ S ], S#Tx => Unit ]
         val id            = tx0.newID()
         private val cnt   = tx0.newIntVar( id, 0 )

         def invoke( key: ReactionKey[ S ])( implicit tx: S#Tx ) {
            map.get( key )( tx.peer ).foreach( _.apply( tx ))
         }

         def add( key: ReactionKey[ S ], fun: S#Tx => Unit )( implicit tx: S#Tx ) {
            map.+=( key -> fun )( tx.peer )
         }

         def remove( key: ReactionKey[ S ])( implicit tx: S#Tx ) {
            map.-=( key )( tx.peer )
         }

         def newID()( implicit tx: S#Tx ) : Int = {
            val res = cnt.get
            cnt.set( res + 1 )
            res
         }
      }
   }
   sealed trait LiveMap[ S <: Sys[ S ]] {
      def newID()( implicit tx: S#Tx ) : Int
      def add( key: ReactionKey[ S ], fun: S#Tx => Unit )( implicit tx: S#Tx ) : Unit
      def remove( key: ReactionKey[ S ])( implicit tx: S#Tx ) : Unit
      def invoke( key: ReactionKey[ S ])( implicit tx: S#Tx ) : Unit
   }

   trait Expr[ A ] {
      def eval( implicit tx: Tx ) : A
   }

   trait StringRef extends Expr[ String ] {
      def append( other: StringRef ) : StringRef
   }

   trait LongRef extends Expr[ Long ] {
      def +( other: LongRef ) : LongRef
      def max( other: LongRef ) : LongRef
      def min( other: LongRef ) : LongRef
   }

   trait Region {
      def name( implicit tx: Tx ) : StringRef
      def name_=( value: StringRef )( implicit tx: Tx ) : Unit
      def name_# : StringRef

      def start( implicit tx: Tx ) : LongRef
      def start_=( value: LongRef ) : Unit
      def start_# : LongRef

      def stop( implicit tx: Tx ) : LongRef
      def stop_=( value: LongRef ) : Unit
      def stop_# : LongRef
   }

   trait RegionList {
      def head( implicit tx: Tx ) : Option[ List[ Region ]]
      def head_=( r: Option[ List[ Region ]]) : Unit
   }

   trait List[ A ] {
      def value: A
      def next( implicit tx: Tx ) : Option[ List[ A ]]
//      def next_=( elem: Option[ List[ A ]])( implicit tx: Tx ) : Unit
   }
}
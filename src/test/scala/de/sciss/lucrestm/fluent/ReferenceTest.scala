package de.sciss.lucrestm
package fluent

import collection.immutable.{IndexedSeq => IIdxSeq}
import concurrent.stm.{TMap, Ref => STMRef}

object ReferenceTest extends App {
   val system = Confluent()

   type Tx = Confluent#Tx

   object Propagator {
      implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Propagator[ S ]] = new Ser[ S ]

      private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Propagator[ S ]] {
         def write( v: Propagator[ S ], out: DataOutput ) { v.write( out )}

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Propagator[ S ] = {
            if( in.readUnsignedByte() == 0 ) {
               new SinkRead[ S ]( in, tx, access )
            } else {
               new ReactionKey[ S ]( in.readInt() )
            }
         }
      }

      private final class SinkRead[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc ) extends Sink[ S ] {
         val id = tx0.readID( in, acc )
         protected val props = tx0.readVar[ IIdxSeq[ Propagator[ S ]]]( id, in )
      }
   }

   object Sink {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : Sink[ S ] = new SinkNew[ S ]( tx )

      private final class SinkNew[ S <: Sys[ S ]]( tx0: S#Tx ) extends Sink[ S ] {
         val id = tx0.newID()
         protected val props = tx0.newVar[ IIdxSeq[ Propagator[ S ]]]( id, IIdxSeq.empty )
      }
   }

   sealed trait Propagator[ S <: Sys[ S ]] extends Writer {
      def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit
   }

   object ReactionKey {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx, map: LiveMap[ S ]) : ReactionKey[ S ] = new ReactionKey( map.newID() )
   }

   final case class ReactionKey[ S <: Sys[ S ]]( id: Int ) extends Propagator[ S ] {
      def write( out: DataOutput ) {
         out.writeInt( id )
      }

      def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) {
         map.invoke( this )
      }
   }

   sealed trait Sink[ S <: Sys[ S ]] extends Propagator[ S ] with Mutable[ S ] {
//      def addSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
//      def removeSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
      final def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) {
//         map.propagate( this )
         props.get.foreach( _.propagate() )
      }

      final protected def writeData( out: DataOutput ) {
         props.write( out )
      }

      final protected def disposeData()( implicit tx: S#Tx ) {
         props.dispose()
      }

      protected def props: S#Var[ IIdxSeq[ Propagator[ S ]]]

//      final def addSink( sink: Sink[ S ])( implicit tx: S#Tx ) {
//         val xs = sinks.get
//         sinks.set( xs :+ sink )
//         if( xs.isEmpty /* && reactions.get.isEmpty */) {
//            deploy()
//         }
//      }
//
//      final def removeSink( sink: Sink[ S ])( implicit tx: S#Tx ) {
//         val xs = sinks.get
//         val i = xs.indexOf( sink )
//         if( i >= 0 ) {
//            val xs1 = xs.patch( i, IIdxSeq.empty, 1 )
//            sinks.set( xs1 )
//            if( xs1.isEmpty /* && reactions.get.isEmpty */) undeploy()
//         }
//      }
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

   trait Event[ S <: Sys[ S ], A ] {
      protected def sink: Sink[ S ]
//      def addSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
//      def removeSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
      protected def deploy()( implicit tx: S#Tx ) : Unit
      protected def undeploy()( implicit tx: S#Tx ) : Unit
      protected def propagate( v: A )( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit

      def addReaction(    react: A => Unit )( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit
      def removeReaction( react: A => Unit )( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit

//      def eval( implicit tx: S#Tx ) : A
   }

   trait EventImpl[ S <: Sys[ S ], A ] extends Event[ S, A ] {
      protected def tx0: S#Tx

//      implicit protected def reactionSer: TxnSerializer[ S#Tx, S#Acc, A => Unit ]

      final val id = tx0.newID()
      private val sinks       = tx0.newVar( id, IIdxSeq.empty[ Propagator[ S ]])
//      private val reactions   = tx0.newVar( id, IIdxSeq.empty[ A => Unit ])
//      private val map = tx0.newVar( id, Map.empty[ Sink[ S ], Int ])

      final def addSink( sink: Sink[ S ])( implicit tx: S#Tx ) {
         val xs = sinks.get
         sinks.set( xs :+ sink )
         if( xs.isEmpty /* && reactions.get.isEmpty */) {
            deploy()
         }
      }

      final def removeSink( sink: Sink[ S ])( implicit tx: S#Tx ) {
         val xs = sinks.get
         val i = xs.indexOf( sink )
         if( i >= 0 ) {
            val xs1 = xs.patch( i, IIdxSeq.empty, 1 )
            sinks.set( xs1 )
            if( xs1.isEmpty /* && reactions.get.isEmpty */) undeploy()
         }
      }

      final def addReaction( react: A => Unit )( implicit tx: S#Tx, map: LiveMap[ S ]) {
         sys.error( "TODO" )
//         val xs = reactions.get
//         reactions.set( xs :+ react )
//         if( xs.isEmpty && sinks.get.isEmpty ) {
//            deploy()
//         }
      }

      final def removeReaction( react: A => Unit )( implicit tx: S#Tx, map: LiveMap[ S ]) {
         sys.error( "TODO" )
//         val xs = reactions.get
//         val i = xs.indexOf( react )
//         if( i >= 0 ) {
//            val xs1 = xs.patch( i, IIdxSeq.empty, 1 )
//            reactions.set( xs1 )
//            if( xs1.isEmpty && sinks.get.isEmpty ) undeploy()
//         }
      }

//      protected final def propagate( v: A )( implicit tx: S#Tx, map: LiveMap[ S ]) {
//         map.getReactions( this ).foreach( _.apply( v ))
//         sinks.get.foreach( _.propagate() )
//      }
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
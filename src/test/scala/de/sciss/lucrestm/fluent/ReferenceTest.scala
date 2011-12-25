package de.sciss.lucrestm
package fluent

import collection.immutable.{IndexedSeq => IIdxSeq}

object ReferenceTest extends App {
   val system = Confluent()

   type Tx = Confluent#Tx

   object Sink {
      implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Sink[ S ]] = new Ser[ S ]

      private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Sink[ S ]] {
         def write( v: Sink[ S ], out: DataOutput ) { v.write( out )}

         def read( in: DataInput, access: S#Acc)( implicit tx: S#Tx ) : Sink[ S ] = {
            new SinkRead[ S ]( in, tx, access )
         }
      }

      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : Sink[ S ] = new SinkNew[ S ]( tx )

      private final class SinkNew[ S <: Sys[ S ]]( tx0: S#Tx ) extends Sink[ S ] {
         val id = tx0.newID()
         protected val sinks = tx0.newVar[ IIdxSeq[ Sink[ S ]]]( id, IIdxSeq.empty )
      }

      private final class SinkRead[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc ) extends Sink[ S ] {
         val id = tx0.readID( in, acc )
         protected val sinks = tx0.readVar[ IIdxSeq[ Sink[ S ]]]( id, in )
      }
   }
   sealed trait Sink[ S <: Sys[ S ]] extends Mutable[ S ] {
//      def addSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
//      def removeSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
      final def propagate()( implicit tx: S#Tx, map: LiveMap[ S ]) {
         map.propagate( this )
         sinks.get.foreach( _.propagate() )
      }

      final protected def writeData( out: DataOutput ) {
         sinks.write( out )
      }

      final protected def disposeData()( implicit tx: S#Tx ) {
         sinks.dispose()
      }

      protected def sinks: S#Var[ IIdxSeq[ Sink[ S ]]]

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
      def apply[ S <: Sys[ S ]]() : LiveMap[ S ] = new Impl[ S ]

      private final class Impl[ S <: Sys[ S ]] extends LiveMap[ S ] {
         def propagate( sink: Sink[ S ])( implicit tx: S#Tx ) {
            sys.error( "TODO" )
         }
      }
   }
   sealed trait LiveMap[ S <: Sys[ S ]] {
//      def addReaction[    A ]( event: Event[ S, A ], fun: A => Unit )( implicit tx: S#Tx ) : Unit
//      def removeReaction[ A ]( event: Event[ S, A ], fun: A => Unit )( implicit tx: S#Tx ) : Unit
//      def getReactions[   A ]( event: Event[ S, A ])( implicit tx: S#Tx ) : Traversable[ A => Unit ]
      def propagate( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
   }

   trait Event[ S <: Sys[ S ], A ] {
      protected def sink: Sink[ S ]
//      def addSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
//      def removeSink( sink: Sink[ S ])( implicit tx: S#Tx ) : Unit
      protected def deploy()( implicit tx: S#Tx ) : Unit
      protected def undeploy()( implicit tx: S#Tx ) : Unit
      protected def propagate( v: A )( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit

      def addReaction( react: A => Unit )( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit
      def removeReaction( react: A => Unit )( implicit tx: S#Tx, map: LiveMap[ S ]) : Unit

//      def eval( implicit tx: S#Tx ) : A
   }

   trait EventImpl[ S <: Sys[ S ], A ] extends Event[ S, A ] {
      protected def tx0: S#Tx

//      implicit protected def reactionSer: TxnSerializer[ S#Tx, S#Acc, A => Unit ]

      final val id = tx0.newID()
      private val sinks       = tx0.newVar( id, IIdxSeq.empty[ Sink[ S ]])
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
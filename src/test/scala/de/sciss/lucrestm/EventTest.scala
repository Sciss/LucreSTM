package de.sciss.lucrestm

import fluent.Confluent
import collection.immutable.{IndexedSeq => IIdxSeq}

object EventTest extends App {
   val system  = Confluent()
   type S      = Confluent

   val e = system.atomic { implicit tx => Event.Bang[ S ]() }

   system.atomic { implicit tx => e.observe { (tx, _) =>
      println( "Bang!" )
   }}

   system.atomic { implicit tx =>
      e.fire()
   }

   val e2 = system.atomic { implicit tx =>
      new Event.Trigger[ S, Int ] {
         protected val targets = Event.Immutable.Targets[ S ]
         protected def disposeData()( implicit tx: S#Tx ) {}
         protected def writeData( out: DataOutput ) {}
         protected def eventSources( implicit tx: S#Tx ) : Event.Sources[ S ] = Event.noSources
      }
   }

   object FilterReader extends Event.Immutable.Serializer[ S, Filter ] {
      def read( in: DataInput, access: S#Acc, _targets: Event.Immutable.Targets[ S ])( implicit tx: S#Tx ) : Filter =
         new Filter {
            protected val targets = _targets
         }
   }

   abstract class Filter extends Event.Immutable.Observable[ S, Int, Filter ] {
      protected def reader = FilterReader
//      protected val targets = Event.Immutable.Targets[ S ]
      protected def disposeData()( implicit tx: S#Tx ) {}
      protected def writeData( out: DataOutput ) {}
      protected def eventSources( implicit tx: S#Tx ) : Event.Sources[ S ] = IIdxSeq( e2 )

      def pull( source: Event.Posted[ S, _ ])( implicit tx: S#Tx ) : Option[ Int ] = {
         e2.pull( source ).flatMap( i => if( i < 10 ) Some( i ) else None )
      }
   }

   val f = system.atomic { implicit tx =>
      new Filter {
         protected val targets = Event.Immutable.Targets[ S ]
      }
   }

   system.atomic { implicit tx =>
      f.observe { (tx, i) =>
         println( "Observed " + i )
      }
   }

   system.atomic { implicit tx =>
      e2.fire(  4 )  // observed
      e2.fire(  8 )  // observed
      e2.fire( 12 )  // filtered out, not observed
   }
}
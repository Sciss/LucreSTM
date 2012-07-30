package de.sciss.lucre
package event

import stm.impl.ConfluentSkel

object EventTest extends App {
   val system  = ConfluentSkel()
   type S      = ConfluentSkel

   val bang = system.step { implicit tx => Bang[ S ]}

   system.step { implicit tx => bang.react { _ => println( "Bang!" )}}

   system.step { implicit tx =>
      bang()
   }

   val e2 = system.step { implicit tx => Trigger[ S, Int ]}

//   object FilterReader extends Event.Invariant.Serializer[ S, Filter ] {
//      def read( in: DataInput, access: S#Acc, _targets: Event.Invariant.Targets[ S ])( implicit tx: S#Tx ) : Filter =
//         new Filter {
//            protected val targets = _targets
//         }
//   }
//
//   abstract class Filter extends Event.Invariant.Observable[ S, Int, Filter ] with Event.LateBinding[ S, Int ] {
//      protected def reader = FilterReader
////      protected val targets = Event.Invariant.Targets[ S ]
//      protected def disposeData()( implicit tx: S#Tx ) {}
//      protected def writeData( out: DataOutput ) {}
//      protected def sources( implicit tx: S#Tx ) : Event.Sources[ S ] = IIdxSeq( e2 )
//
//      def pull( source: Event.Posted[ S, _ ])( implicit tx: S#Tx ) : Option[ Int ] = {
//         e2.pull( source ).flatMap( i => if( i < 10 ) Some( i ) else None )
//      }
//   }
//

//   val f = system.atomic { implicit tx =>
////      implicit def ser: TxnSerializer[ S#Tx, S#Acc, Event.Trigger.Standalone[ S, Int ]] = Event.Trigger.Standalone.serializer[ S, Int ]
////      e2.filter[ Event.Trigger.Standalone[ S, Int ], Int => Boolean ]( (_: Int) < 10 )
//      e2.filter( _ < 10 )
//   }
//
//   system.atomic { implicit tx =>
//      f.observe { (tx, i) =>
//         println( "Observed " + i )
//      }
//   }

   system.step { implicit tx =>
      e2(  4 )  // observed
      e2(  8 )  // observed
      e2( 12 )  // filtered out, not observed
   }
}
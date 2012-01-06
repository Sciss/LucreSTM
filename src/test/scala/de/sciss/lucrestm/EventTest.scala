package de.sciss.lucrestm

import fluent.Confluent

object EventTest {
   val system  = Confluent()
   type S      = Confluent

   trait TestEvent extends Event.Immutable[ S, Unit ] with Event.Observable[ S, Unit, TestEvent ] {
      final protected def disposeData()( implicit tx: S#Tx ) {}
      final protected def writeData( out: DataOutput ) {}
      final protected def eventSources( implicit tx: S#Tx ) : Event.Sources[ S ] = Event.noSources

      final def pull( source: Event.Posted[ S ])( implicit tx: S#Tx ) : Option[ Unit ] = Some( () )

//      protected val reader = new Event.Immutable.Serializer[ S, TestEvent ] {
//
//      }

      def observe( fun: (S#Tx, Unit) => Unit )( implicit tx: S#Tx ) : Event.Observer[ S, Unit, TestEvent ] = {
//         Event.Observer[ S, Unit, TestEvent ]( reader, fun )
         sys.error( "TODO" )
      }
   }

   val e = system.atomic { implicit tx =>
      new TestEvent {
         protected val targets = Event.Immutable.Targets[ S ]( tx )
      }
   }

//   Event.Observer.apply( reader, fun )
}
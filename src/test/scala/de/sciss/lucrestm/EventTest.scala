package de.sciss.lucrestm

import fluent.Confluent
import collection.immutable.{IndexedSeq => IIdxSeq}

object EventTest extends App {
   val system  = Confluent()
   type S      = Confluent

   val reader = new Event.Immutable.Serializer[ S, Bang ] {
      def read( in: DataInput, access: S#Acc, _targets: Event.Immutable.Targets[ S ])( implicit tx: S#Tx ) : Bang =
         new Bang {
            protected val targets = _targets
         }
   }

   trait Bang extends Event.Immutable[ S, Unit ] with Event.Observable[ S, Unit, Bang ] {
      final protected def disposeData()( implicit tx: S#Tx ) {}
      final protected def writeData( out: DataOutput ) {}
      final protected def eventSources( implicit tx: S#Tx ) : Event.Sources[ S ] = Event.noSources

      final def pull( source: Event.Posted[ S ])( implicit tx: S#Tx ) : Option[ Unit ] = {
         if( source.source == this ) Some( () ) else None
      }

      def observe( fun: (S#Tx, Unit) => Unit )( implicit tx: S#Tx ) : Event.Observer[ S, Unit, Bang ] = {
         val res = Event.Observer[ S, Unit, Bang ]( reader, fun )
         res.add( this )
         res
      }

      final def bang()( implicit tx: S#Tx ) {
         val posted     = Event.Posted( this, -1 )
         val reactions  = propagate( posted, this, IIdxSeq.empty )
         reactions.map( _.apply() ).foreach( _.apply() )
      }
   }

   val e = system.atomic { implicit tx =>
      new Bang {
         protected val targets = Event.Immutable.Targets[ S ]( tx )
      }
   }

   system.atomic { implicit tx => e.observe { (tx, _) =>
      println( "Bang!" )
   }}

   system.atomic { implicit tx =>
      e.bang()
   }
}
package de.sciss.lucre
package expr

import stm.Sys
import collection.immutable.{IndexedSeq => IIdxSeq}
import stm.impl.{BerkeleyDB, InMemory, Confluent}
import java.io.File
import event._

object MutatingTest extends App {
   private def memorySys    : (InMemory, () => Unit) = (InMemory(), () => ())
   private def confluentSys : (Confluent, () => Unit) = (Confluent(), () => ())
   private def databaseSys  : (BerkeleyDB, () => Unit) = {
      val file = new File( new File( new File( sys.props( "user.home" ), "Desktop" ), "mutating" ), "data" )
      val db   = BerkeleyDB.open( file )
      (db, () => db.close())
   }

   args.toSeq.take( 2 ) match {
      case Seq( "--memory" )      => run[ InMemory ]( memorySys )
      case Seq( "--confluent" )   => run( confluentSys )
      case Seq( "--database" )    => run( databaseSys )
      case _  => println( """
Usage:
   --memory
   --confluent
   --database
""" )
   }

   def run[ S <: Sys[ S ]]( setup: (S, () => Unit) ) {
      val (system, cleanUp) = setup
      try {
         system.atomic { implicit tx =>
            val m = apply( tx )
            import m._
            import regions._

            val unsorted   = RegionList.empty
            val sorted     = Sorted( unsorted )
//            sorted.changed.reactTx { implicit tx => {
//               case Sorted.Added(   _, region ) => println( "Added: " + region.name.value + " @ " + region.span.value )
//               case Sorted.Removed( _, region ) => println( "Removed: " + region.name.value + " @ " + region.span.value )
//               case Sorted.Element( _, chs )    => chs.foreach( ch => println( "Changed: " + ch ))
//            }}

            println( "\nInitial: (_should re-validate_)" )
            sorted.toList  // make sure it's validated, to ensure that re-validation actually works!

            val rnd = new scala.util.Random( 0L )
            (1 to 2).foreach { i =>
               val start = (rnd.nextDouble() * 441000L).toLong
               val stop  = start + (rnd.nextDouble() * 441000L).toLong
               val r = EventRegion( "r" + i, Span( start, stop ))
               unsorted.add( r )
            }

            println( "\nSorted: (_should re-validate_)" )
            println( sorted.toList.map( r => r.name.value + " @ " + r.span.value ).mkString( "\n" ))

            println( "\nTrying again... (_should NOT re-validate_)" )
            sorted.toList

            println( "\nNow observed..." )
            sorted.changed.reactTx { implicit tx => {
               case Sorted.Added(   _, region ) => println( "Added: " + region.name.value + " @ " + region.span.value )
               case Sorted.Removed( _, region ) => println( "Removed: " + region.name.value + " @ " + region.span.value )
               case Sorted.Element( _, chs )    => chs.foreach( ch => println( "Changed: " + ch ))
            }}

            val r = EventRegion( "rx", Span( 12345, 67890 ))
            unsorted.add( r )

            println( "\nTrying again... (_should NOT re-validate_)" )
            sorted.toList
         }

      } finally {
         cleanUp()
      }
   }

   def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : MutatingTest[ S ] = {
      val strings = Strings[ S ]
      val longs   = Longs[ S ]
      val spans   = Spans[ S ]( longs )
      val regions = new Regions[ S ]( strings, longs, spans )
      new MutatingTest[ S ]( regions )
   }
}

class MutatingTest[ S <: Sys[ S ]]( val regions: Regions[ S ]) {
   import regions._

   object Sorted extends Decl[ S, Sorted ] {
      sealed trait Collection extends Update { def l: Sorted; def region: EventRegion }
      final case class Added(   l: Sorted, region: EventRegion ) extends Collection
      final case class Removed( l: Sorted, region: EventRegion ) extends Collection
      final case class Element( l: Sorted, changes: IIdxSeq[ EventRegion.Changed ]) extends Update

      declare[ Collection ]( _.collectionChanged )
      declare[ Element    ]( _.elementChanged    )

      def apply[ A ]( unsorted: RegionList )( implicit tx: S#Tx ) : Sorted = new New( tx, unsorted )

      val serializer : event.Reader[ S, Sorted ] = new NodeSerializer[ S, Sorted ] {
         def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Sorted =
            new Read( in, access, targets, tx )
      }

      private type RegionSeq = IIdxSeq[ EventRegion ]

      private sealed trait Impl extends Sorted {
         protected def seq : S#Var[ RegionSeq ]
         protected def unsorted: RegionList

//         final lazy val collectionChanged = event[ Collection ]
         final lazy val collectionChanged = unsorted.collectionChanged.mapAndMutate[ Collection ] { implicit tx => {
            case RegionList.Added(   _, _, region ) => add(    region ); Added(   this, region )
            case RegionList.Removed( _, _, region ) => remove( region ); Removed( this, region )
         }}
         final lazy val elementChanged    = unsorted.elementChanged.map( e => Element( this, e.changes ))
         final lazy val changed           = collectionChanged | elementChanged

         final protected def decl = Sorted

         final def toList( implicit tx: S#Tx ) : List[ Elem ] = {
            ensureValidity()
            seq.get.toList
         }

         final protected def ensureValidity()( implicit tx: S#Tx ) {
//            if( targets.isInvalid ) {
//println( "VALIDATING" )
//               val sz = unsorted.size
//               var idx = 0; while( idx < sz ) {
//                  add( unsorted( idx ))
//               idx += 1 }
//               targets.validated()
//            }
         }

         override def toString = "Sorted" + id

         final protected def add( elem: Elem )( implicit tx: S#Tx ) {
println( "ADD" )
            val es         = seq.get
            val newStart   = elem.span.value.start
            // Obviously we'd have at least a binary search here in a real application...
            val idx0       = es.indexWhere( _.span.value.start > newStart )
            val idx        = if( idx0 >= 0 ) idx0 else es.size
            val esNew      = es.patch( idx, IIdxSeq( elem ), 0 )
            seq.set( esNew )
//            collectionChanged( Added( this, elem ))
         }

         private def remove( elem: Elem )( implicit tx: S#Tx ) {
            val es         = seq.get
            val idx        = es.indexOf( elem )
            if( idx < 0 ) return
            val esNew      = es.patch( idx, IIdxSeq.empty, 1 )
            seq.set( esNew )
         }

         final protected def disposeData()( implicit tx: S#Tx ) {
            seq.dispose()
         }

         final protected def writeData( out: DataOutput ) {
            unsorted.write( out )
            seq.write( out )
         }
      }

      private final class New( tx0: Tx, protected val unsorted: RegionList ) extends Impl {
         protected val targets   = Targets[ S ]( tx0 )
         protected val seq       = tx0.newVar[ RegionSeq ]( id, IIdxSeq.empty )

//         // ---- constructor ----
//         connectNode()( tx0 )
//         ensureValidity()( tx0 )
      }

      private final class Read( in: DataInput, access: S#Acc, protected val targets: Targets[ S ], tx0: S#Tx )
      extends Impl {
         protected val unsorted  = RegionList.serializer.read( in, access )( tx0 )
         protected val seq       = tx0.readVar[ RegionSeq ]( id, in )
      }
   }

   trait Sorted extends Compound[ S, Sorted, Sorted.type ] {
      import Sorted._
      def collectionChanged:  Ev[ Collection ]
      def elementChanged:     Ev[ Element ]
      def changed:            Ev[ Update ]

      protected type Elem  = EventRegion

      def toList( implicit tx: S#Tx ) : List[ Elem ]
   }
}

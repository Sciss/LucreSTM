package de.sciss.lucre
package expr

import stm.{Mutable, Sys, TxnSerializer}
import annotation.tailrec
import event.{Compound, Decl, Mutating}

object MutatingTest {
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
      def apply[ A ]( unsorted: RegionList ) : Sorted = sys.error( "TODO" )

      val serializer : event.Reader[ S, Sorted, _ ] = sys.error( "TODO" )
   }

   trait Sorted extends Mutating[ S, Sorted.Update ] with Compound[ S, Sorted, Sorted.type ] {
      def toList( implicit tx: S#Tx ) : List[ EventRegion ]
   }
}

package de.sciss.lucre
package expr

import stm.Sys
import event.Invariant
import concurrent.stm.{InTxn, TMap}

//object Extensions {
//   trait ReaderFactory[ S <: Sys[ S ], A ] {
//      def reader[ S <: Sys[ S ]] : Invariant.Reader[ S, Expr[ S, A ]]
//   }
//}

trait Extensions[ S <: Sys[ S ], A ] {
   private val map = TMap.empty[ Int, TupleReader[ S, A ]]

   final def getExtension( tpe: Int )( implicit tx: InTxn ) : TupleReader[ S, A ] = {
      map.get( tpe ).getOrElse( sys.error( "No registered extensions from type " + tpe ))
   }

   final def addExtension( tpe: Type[ S, _ ], reader: TupleReader[ S, A ])( implicit tx: InTxn ) {
      map += ((tpe.id, reader))
   }

   final def removeExtension( tpe: Type[ S, _ ])( implicit tx: InTxn ) {
      map -= tpe.id
   }
}
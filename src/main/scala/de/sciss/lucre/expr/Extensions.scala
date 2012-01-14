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
   private val map = TMap.empty[ Int, Invariant.Reader[ S, Expr[ S, A ]]]

   final def readExtension( cookie: Int, in: DataInput, access: S#Acc,
                            targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Expr[ S, A ] = {
      implicit val itx = tx.peer
      val rf = map.get( cookie ).getOrElse( sys.error( "Unregistered extension " + cookie ))
      rf.read( in, access, targets )
   }

   final def addExtension( cookie: Int, reader: Invariant.Reader[ S, Expr[ S, A ]])( implicit tx: InTxn ) {
      map += ((cookie, reader))
   }

   final def removeExtension( cookie: Int )( implicit tx: InTxn ) {
      map -= cookie
   }
}
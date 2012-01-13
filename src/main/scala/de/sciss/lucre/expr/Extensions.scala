package de.sciss.lucre
package expr

import stm.Sys
import event.Invariant
import expr.Extensions.ReaderFactory
import concurrent.stm.{InTxn, TMap}

object Extensions {
   trait ReaderFactory[ A ] {
      def reader[ S <: Sys[ S ]] : Invariant.Reader[ S, Expr[ S, A ]]
   }
}

trait Extensions[ A ] {
   private val map = TMap.empty[ Int, ReaderFactory[ A ]]

   final def readExtension[ S <: Sys[ S ]]( cookie: Int, in: DataInput, access: S#Acc,
                                            targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Expr[ S, A ] = {
      implicit val itx = tx.peer
      val rf = map.get( cookie ).getOrElse( sys.error( "Unregistered extension " + cookie ))
      rf.reader[ S ].read( in, access, targets )
   }

   final def addExtension( cookie: Int, factory: ReaderFactory[ A ])( implicit tx: InTxn ) {
      map += ((cookie, factory))
   }

   final def removeExtension( cookie: Int )( implicit tx: InTxn ) {
      map -= cookie
   }
}
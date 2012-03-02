package de.sciss.lucre
package expr

import stm.Sys
import event.Targets

trait TupleReader[ S <: Sys[ S ], A ] {
   def readTuple( arity: Int, opID: Int, in: DataInput, access: S#Acc, targets: Targets[ S ])
                ( implicit tx: S#Tx ) : Expr[ S, A ]
}
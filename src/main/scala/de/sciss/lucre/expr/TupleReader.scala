package de.sciss.lucre
package expr

import stm.Sys
import event.Invariant

trait TupleReader[ S <: Sys[ S ], A ] {
   def readTuple( arity: Int, opID: Int, in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])
                ( implicit tx: S#Tx ) : Expr[ S, A ]
}
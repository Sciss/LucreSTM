package dummy

import de.sciss.lucre._

object NoRepr {
   def test[ S <: stm.Sys[ S ], T <: stm.Sys[ T ]]( implicit bridge: S#Tx => T#Tx ) {}

   test[ stm.InMemory, stm.InMemory ] // ( identity )
   test[ stm.Durable, stm.InMemory ]
   test[ stm.impl.ConfluentSkel, stm.InMemory ]
//   test[ stm.InMemory, stm.Durable ]
}

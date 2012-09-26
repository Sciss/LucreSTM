//package dummy
//
//import dummy.{Sys => _}
//import de.sciss.lucre._
//import stm._
//
//object Verify {
//   class Support[ S <: stm.Sys[ S ]]( val intVar: S#Var[ Int ])
//
//   class Test[ S <: stm.Sys[ S ]]( intVar: S#IM#Var[ Int ]) {
//      def update( value: Int )( implicit tx: S#Tx ) {
//         val s = tx.system
//         implicit val itx = s.im( tx )
////         val is = itx.system
//         val iv = s.imVar( intVar )
////         val iv = is.fixVar( intVar )
//         iv.set( value )
//      }
//   }
//
////   class Test[ S <: stm.Sys[ S ]]( sup: Support[ S#IM ]) {
////      def update( value: Int )( implicit tx: S#Tx ) {
////         val s = tx.system
////         implicit val itx = s.fixIM( tx )
////         val f = s.fixIM( sup )
////         f.intVar.set( value )
////      }
////   }
//
//   def apply[ S <: stm.Sys[ S ]]( implicit tx: S#Tx, cursor: Cursor[ S ]) : Test[ S ] = {
//      implicit val itx = tx.system.im( tx )
//      val iid        = itx.newID() // fixIM( itx.newID() )
////         val infoVar    = itx.newVar( iid, Info.init ) // ( dummySerializer )
//      val intVar     = itx.newIntVar( iid, 0 )
////      val sup        = new Support[ S#IM ]( intVar )
//      val t          = new Test[ S ]( intVar ) // sup )
//      t
//   }
//}

//package de.sciss.lucre.stm
//
//object LocalTest extends App {
//
//   def test[ S <: Sys[ S ]]()( implicit cursor: Cursor[ S ]) {
//      cursor.step { implicit tx =>
//         val itx = tx.inMemory
//         itx.newLocal { implicit tx =>
//
//         }
//      }
//   }
//}

/*
 *  Mutable.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre
package stm

object Mutable {
   trait Impl[ S <: Sys[ S ]] extends Mutable[ S ] {
      final def dispose()( implicit tx: S#Tx ) {
         id.dispose()
         disposeData()
      }

      final def write( out: DataOutput ) {
         id.write( out )
         writeData( out )
      }

      protected def disposeData()( implicit tx: S#Tx ) : Unit
      protected def writeData( out: DataOutput ) : Unit

      override def equals( that: Any ) : Boolean = {
         // note: microbenchmark shows that an initial this eq that.asInstanceOf[AnyRef] doesn't improve performance at all
         /* (that != null) && */ (if( that.isInstanceOf[ Mutable[ _ ]]) {
            id == that.asInstanceOf[ Mutable[ _ ]].id
         } else super.equals( that ))
      }

      override def hashCode = id.hashCode()

      override def toString = super.toString + id.toString
   }
}
trait Mutable[ S <: Sys[ S ]] extends Identifiable[ S#ID ] with Writable with Disposable[ S#Tx ]

/**
 * Note: Since a reader goes along with `A` implementing the writer,
 * it does not make sense to make `MutableReader` covariant in `A`.
 */
trait MutableReader[ -ID, -Txn, A ] {
   def readData( in: DataInput, id: ID )( implicit tx: Txn ) : A
}

//trait MutableOptionReader[ -ID, -Txn, A ] extends MutableReader[ ID, Txn, A ] {
//   def empty: A
//}

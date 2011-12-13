/*
 *  Mutable.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.lucrestm

sealed trait MutableOption[ +S ] {
//   def toOption: Option[ Mutable[ S ]]
//   def orNull: Mutable[ S ]
}

trait EmptyMutable extends MutableOption[ Nothing ] {
//   final def toOption = None
//   final def orNull   = null
}

trait Mutable[ S <: Sys[ S ]] extends MutableOption[ S ] with Writer with Disposable[ S#Tx ] {
   //AnyRef =>

   def id: S#ID

//   final def toOption   = Some( this )
//   final def orNull     = this

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

//   final def sameAs( that: Mutable[ S ]) = id == that.id

   override def equals( that: Any ) : Boolean = {
      // note: microbenchmark shows that an initial this eq that.asInstanceOf[AnyRef] doesn't improve performance at all
      /* (that != null) && */ (if( that.isInstanceOf[ Mutable[ _ ]]) {
         id == that.asInstanceOf[ Mutable[ _ ]].id
      } else super.equals( that ))
   }

   override def toString = super.toString + id.toString

   // ---- mutable elements ----
   protected final def newVal[ A ]( init: A )( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Val[ A ] =
      tx.newVal[ A ]( id, init )

   protected final def newInt( init: Int )( implicit tx: S#Tx ) : S#Val[ Int ] =
      tx.newInt( id, init )

   protected final def newRef[ A <: Mutable[ S ]]( init: A )
                                                 ( implicit tx: S#Tx, reader: MutableReader[ S, A ]) : S#Ref[ A ] =
       tx.newRef[ A ]( id, init )

   protected final def newOptionRef[ A <: MutableOption[ S ]]( init: A )
                                                             ( implicit tx: S#Tx,
                                                               reader: MutableOptionReader[ S, A ]) : S#Ref[ A ] =
      tx.newOptionRef[ A ]( id, init )

   protected final def newValArray[ A ]( size: Int )( implicit tx: S#Tx ) : Array[ S#Val[ A ]] =
      tx.newValArray[ A ]( size )

   protected final def newRefArray[ A ]( size: Int )( implicit tx: S#Tx ) : Array[ S#Ref[ A ]] =
      tx.newRefArray[ A ]( size )
}

/**
 * Note: Sicne a reader goes along with `A` implementing the writer,
 * it does not make sense to make `MutableReader` covariant in `A`.
 */
trait MutableReader[ S <: Sys[ S ], /* + */ A ] {
   def readData( in: DataInput, id: S#ID ) : A
}

trait MutableOptionReader[ S <: Sys[ S ], /* + */ A ] extends MutableReader[ S, A ] {
   def empty: A
}
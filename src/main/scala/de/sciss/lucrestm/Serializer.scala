/*
 *  Serializer.scala
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

trait Writer {
   def write( out: DataOutput ) : Unit
}

trait Reader[ @specialized +A ] {
   def read( in: DataInput ) : A
}

object Serializer {
   implicit object Int extends Serializer[ scala.Int ] {
      def write( v: scala.Int, out: DataOutput ) {
         out.writeInt( v )
      }
      def read( in: DataInput ) : scala.Int = in.readInt()
   }

   implicit def fromReader[ A <: Writer ]( implicit reader: Reader[ A ]) : Serializer[ A ] =  new ReaderWrapper( reader )

   implicit def fromMutableReader[ S <: Sys[ S ], A >: Null <: Mutable[ S ]]( implicit reader: MutableReader[ S, A ],
                                                                              system: S ) : Serializer[ A ] =
      new MutableReaderWrapper[ S, A ]

   private final class ReaderWrapper[ A <: Writer ]( reader: Reader[ A ]) extends Serializer[ A ] {
      def write( v: A, out: DataOutput ) { v.write( out )}
      def read( in: DataInput ) : A = reader.read( in )
   }

   private final class MutableReaderWrapper[ S <: Sys[ S ], A >: Null <: Mutable[ S ]](
      implicit reader: MutableReader[ S, A ], system: S ) extends Serializer[ A ] {

      def write( v: A, out: DataOutput ) { v.write( out )}
      def read( in: DataInput ) : A = system.readMut[ A ]( in )
   }
}
trait Serializer[ @specialized A ] extends Reader[ A ] {
   def write( v: A, out: DataOutput ) : Unit
//   def read( in: DataInput ) : A
}
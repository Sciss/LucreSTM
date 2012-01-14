/*
 *  Serializer.scala
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

import annotation.switch
import collection.immutable.{IndexedSeq => IIdxSeq}
import collection.mutable.Builder

object Serializer {
   // ---- primitives ----

   implicit object Boolean extends Serializer[ scala.Boolean ] {
      def write( v: scala.Boolean, out: DataOutput ) {
         out.writeBoolean( v )
      }
//      def read( in: DataInput ) : scala.Boolean = in.readBoolean()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : scala.Boolean = in.readBoolean()
   }

   implicit object Char extends Serializer[ scala.Char ] {
      def write( v: scala.Char, out: DataOutput ) {
         out.writeChar( v )
      }
//      def read( in: DataInput ) : scala.Char = in.readChar()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : scala.Char = in.readChar()
   }

   implicit object Int extends Serializer[ scala.Int ] {
      def write( v: scala.Int, out: DataOutput ) {
         out.writeInt( v )
      }
//      def read( in: DataInput ) : scala.Int = in.readInt()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : scala.Int = in.readInt()
   }

   implicit object Float extends Serializer[ scala.Float ] {
      def write( v: scala.Float, out: DataOutput ) {
         out.writeFloat( v )
      }
//      def read( in: DataInput ) : scala.Float = in.readFloat()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : scala.Float = in.readFloat()
   }

   implicit object Long extends Serializer[ scala.Long ] {
      def write( v: scala.Long, out: DataOutput ) {
         out.writeLong( v )
      }
//      def read( in: DataInput ) : scala.Long = in.readLong()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : scala.Long = in.readLong()
   }

   implicit object Double extends Serializer[ scala.Double ] {
      def write( v: scala.Double, out: DataOutput ) {
         out.writeDouble( v )
      }
//      def read( in: DataInput ) : scala.Double = in.readDouble()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : scala.Double = in.readDouble()
   }

   implicit object String extends Serializer[ java.lang.String ] {
      def write( v: java.lang.String, out: DataOutput ) {
         out.writeString( v )
      }
//      def read( in: DataInput ) : java.lang.String = in.readString()
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : java.lang.String = in.readString()
   }

   // ---- incremental build-up ----

   implicit def fromReader[ A <: Writer ]( implicit reader: Reader[ A ]) : Serializer[ A ] = new ReaderWrapper( reader )

   private final class ReaderWrapper[ A <: Writer ]( reader: Reader[ A ]) extends Serializer[ A ] {
      def write( v: A, out: DataOutput ) { v.write( out )}
//      def read( in: DataInput ) : A = reader.read( in )
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : A = reader.read[ S ]( in, access )
   }

   // ---- higher-kinded ----

   implicit def option[ A ]( implicit peer: Serializer[ A ]) : Serializer[ Option[ A ]] = new OptionWrapper[ A ]( peer )

   private final class OptionWrapper[ @specialized A ]( peer: Serializer[ A ])
   extends Serializer[ Option[ A ]] {
      def write( opt: Option[ A ], out: DataOutput ) { opt match {
         case Some( v ) => out.writeUnsignedByte( 1 ); peer.write( v, out )
         case None      => out.writeUnsignedByte( 0 )
      }}

//      def read( in: DataInput ) : Option[ A ] = (in.readUnsignedByte(): @switch) match {
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Option[ A ] =
         (in.readUnsignedByte(): @switch) match {
            case 1 => Some( peer.read( in, access ))
            case 0 => None
         }
   }

   implicit def either[ A, B ]( implicit peer1: Serializer[ A ], peer2: Serializer[ B ]) : Serializer[ Either[ A, B ]] =
      new EitherWrapper[ A, B ]( peer1, peer2 )

   private final class EitherWrapper[ @specialized( scala.Int, scala.Float, scala.Double, scala.Long, scala.Char ) A,
                                      @specialized( scala.Int, scala.Float, scala.Double, scala.Long, scala.Char ) B ](
      peer1: Serializer[ A ], peer2: Serializer[ B ]) extends Serializer[ Either[ A, B ]] {

      def write( eith: Either[ A, B ], out: DataOutput ) { eith match {
         case Left( a )  => out.writeUnsignedByte( 0 ); peer1.write( a, out )
         case Right( b ) => out.writeUnsignedByte( 1 ); peer2.write( b, out )
      }}

//      def read( in: DataInput ) : Either[ A, B ] = (in.readUnsignedByte(): @switch) match {
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Either[ A, B ] =
         (in.readUnsignedByte(): @switch) match {
            case 0 => Left(  peer1.read( in, access ))
            case 1 => Right( peer2.read( in, access ))
         }
   }

   implicit def tuple2[ A1, A2 ]( implicit peer1: Serializer[ A1 ],
                                  peer2: Serializer[ A2 ]) : Serializer[ (A1, A2) ] =
      new Tuple2Wrapper[ A1, A2 ]( peer1, peer2 )

   private final class Tuple2Wrapper[ @specialized( scala.Int, scala.Float, scala.Double, scala.Long, scala.Char ) A1,
                                      @specialized( scala.Int, scala.Float, scala.Double, scala.Long, scala.Char ) A2 ](
      peer1: Serializer[ A1 ], peer2: Serializer[ A2 ]) extends Serializer[ (A1, A2) ] {

      def write( tup: (A1, A2), out: DataOutput ) {
         peer1.write( tup._1, out )
         peer2.write( tup._2, out )
      }

//      def read( in: DataInput ) : (A1, A2) = {
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : (A1, A2) = {
         val a1 = peer1.read( in, access )
         val a2 = peer2.read( in, access )
         (a1, a2)
      }
   }

   implicit def tuple3[ A1, A2, A3 ]( implicit peer1: Serializer[ A1 ], peer2: Serializer[ A2 ],
                                      peer3: Serializer[ A3 ]) : Serializer[ (A1, A2, A3) ] =
      new Tuple3Wrapper[ A1, A2, A3 ]( peer1, peer2, peer3 )

   private final class Tuple3Wrapper[ A1, A2, A3 ](
      peer1: Serializer[ A1 ], peer2: Serializer[ A2 ], peer3: Serializer[ A3 ])
   extends Serializer[ (A1, A2, A3) ] {

      def write( tup: (A1, A2, A3), out: DataOutput ) {
         peer1.write( tup._1, out )
         peer2.write( tup._2, out )
         peer3.write( tup._3, out )
      }

//      def read( in: DataInput ) : (A1, A2, A3) = {
      def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : (A1, A2, A3) = {
         val a1 = peer1.read( in, access )
         val a2 = peer2.read( in, access )
         val a3 = peer3.read( in, access )
         (a1, a2, a3)
      }
   }

   implicit def list[ A ]( implicit peer: Serializer[ A ]) : Serializer[ List[ A ]] = new ListSerializer[ A ]( peer )

   implicit def set[ A ]( implicit peer: Serializer[ A ]) : Serializer[ Set[ A ]] = new SetSerializer[ A ]( peer )

   implicit def indexedSeq[ A ]( implicit peer: Serializer[ A ]) : Serializer[ IIdxSeq[ A ]] =
      new IndexedSeqSerializer[ A ]( peer )

   implicit def map[ A, B ]( implicit peer: Serializer[ (A, B) ]) : Serializer[ Map[ A, B ]] =
      new MapSerializer[ A, B ]( peer )

   // XXX size might be a slow operation on That...
   private sealed trait CollectionSerializer[ A, That <: Traversable[ A ]] extends Serializer[ That ] {
      def newBuilder: Builder[ A, That ]
      def peer: Serializer[ A ]

      final def write( coll: That, out: DataOutput ) {
         out.writeInt( coll.size )
         val ser = peer
         coll.foreach( ser.write( _, out ))
      }

//      final def read( in: DataInput ) : That = {
      final def read[ S <: Sys[ S ]]( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : That = {
         var sz   = in.readInt()
         val b    = newBuilder
         val ser  = peer
         while( sz > 0 ) {
            b += ser.read( in, access )
         sz -= 1 }
         b.result()
      }
   }

   private final class ListSerializer[ A ]( val peer: Serializer[ A ])
   extends CollectionSerializer[ A, List[ A ]] {
      def newBuilder = List.newBuilder[ A ]
   }

   private final class SetSerializer[ A ]( val peer: Serializer[ A ])
   extends CollectionSerializer[ A, Set[ A ]] {
      def newBuilder = Set.newBuilder[ A ]
   }

   private final class IndexedSeqSerializer[ A ]( val peer: Serializer[ A ])
   extends CollectionSerializer[ A, IIdxSeq[ A ]] {
      def newBuilder = IIdxSeq.newBuilder[ A ]
   }

   private final class MapSerializer[ A, B ]( val peer: Serializer[ (A, B) ])
   extends CollectionSerializer[ (A, B), Map[ A, B ]] {
      def newBuilder = Map.newBuilder[ A, B ]
   }
}

trait Serializer[ @specialized A ] extends Reader[ A ] { // with TxnSerializer[ Any, Any, A ]
   def write( v: A, out: DataOutput ) : Unit
}
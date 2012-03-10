/*
 *  Longs.scala
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
package expr

import annotation.switch
import stm.{InMemory, Sys}
import event.Targets

object Longs {
   def apply[ S <: Sys[ S ]] : Longs[ S ] = new Longs[ S ]
}

final class Longs[ S <: Sys[ S ]] extends Type[ S, Long ] {
   tpe =>

   val id = 3

   protected def writeValue( v: Long, out: DataOutput ) { out.writeLong( v )}
   protected def readValue( in: DataInput ) : Long = in.readLong()
//   type Ops = LongOps

   // for a stupid reason scalac doesn't eat A <% Ex
   implicit def longOps[ A <% Expr[ S, Long ]]( ex: A ) : LongOps = new LongOps( ex )

   final class LongOps private[Longs]( ex: Ex ) {
      def +( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Plus( ex, that )
      def -( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Minus( ex, that )
      def min( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Min( ex, that )
      def max( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Max( ex, that )
      def abs( implicit tx: S#Tx ) : Ex = UnaryOp.Abs( ex )
   }

   def readTuple( arity: Int, opID: Int, in: DataInput, access: S#Acc,
                  targets: Targets[ S ])( implicit tx: S#Tx ) : Ex = {
      (arity: @switch) match {
         case 1 => UnaryOp(  opID ).read( in, access, targets )
         case 2 => BinaryOp( opID ).read( in, access, targets )
      }
   }

   private object UnaryOp {
      def apply( id: Int ) : UnaryOp = (id /*: @switch */) match {
         case 0 => Abs
      }

      sealed trait Basic extends UnaryOp {
         final def apply( _1: Ex )( implicit tx: S#Tx ) : Ex =
            new Tuple1( tpe.id, this, Targets[ S ], _1 )

         final def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Ex = {
            val _1 = readExpr( in, access )
            new Tuple1( tpe.id, this, targets, _1 )
         }
      }

      object Abs extends Basic {
         val id = 0
         def value( in: Long ) = math.abs( in )

         def toString( _1: Ex ) = "abs(" + _1 + ")"
      }
   }

//   protected def binaryOp( id: Int ) = BinaryOp( id )

   private object BinaryOp {
      def apply( id: Int ) : Tuple2Op[ Long, Long ] = (id: @switch) match {
         case 0 => Plus
         case 1 => Minus
         case 2 => Min
         case 3 => Max
      }

      sealed trait Basic extends BinaryOp {
         final def apply( _1: Ex, _2: Ex )( implicit tx: S#Tx ) : Ex =
            new Tuple2( tpe.id, this, Targets[ S ], _1, _2 )

         final def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Ex = {
            val _1 = readExpr( in, access )
            val _2 = readExpr( in, access )
            new Tuple2( tpe.id, this, targets, _1, _2 )
         }
      }

      object Plus extends Basic  {
         val id = 0
         def value( a: Long, b: Long ) = a + b

         def toString( _1: Ex, _2: Ex ) = "(" + _1 + " + " + _2 + ")"
      }

      object Minus extends Basic {
         val id = 1
         def value( a: Long, b: Long ) = a - b

         def toString( _1: Ex, _2: Ex ) = "(" + _1 + " - " + _2 + ")"
      }

      object Min extends Basic {
         val id = 2
         def value( a: Long, b: Long ) = math.min( a, b )

         def toString( _1: Ex, _2: Ex ) = "min(" + _1 + ", " + _2 + ")"
      }

      object Max extends Basic {
         val id = 3
         def value( a: Long, b: Long ) = math.max( a, b )

         def toString( _1: Ex, _2: Ex ) = "max(" + _1 + ", " + _2 + ")"
      }
   }
}

object LongsTests extends App {
   new LongTests( InMemory() )
}

class LongTests[ S <: Sys[ S ]]( system: S ) {
   val strings = new Longs[ S ]
   import strings._
   import system.{ atomic => ◊ }

   val s    = ◊ { implicit tx => Var( 33 )}
   val s1   = ◊ { implicit tx => s - 50 }
   val s2   = ◊ { implicit tx => s1.abs }
   val eval = ◊ { implicit tx => s2.value }

   println( "Evaluated: " + eval )

   ◊ { implicit tx => s2.changed.react { ch => println( "Observed: " + ch )}}

   ◊ { implicit tx => s.set( 22 )}
}

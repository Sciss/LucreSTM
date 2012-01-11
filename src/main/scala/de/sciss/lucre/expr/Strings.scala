/*
 *  Strings.scala
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

import stm.Sys
import event.{Event, Invariant, LateBinding, Sources, StandaloneLike}
import annotation.switch
import stm.impl.InMemory
import collection.immutable.{IndexedSeq => IIdxSeq}

final class Strings[ S <: Sys[ S ]] extends Type[ S, String ] {
   protected def writeValue( v: String, out: DataOutput ) { out.writeString( v )}
   protected def readValue( in: DataInput ) : String = in.readString()

   final class Ops( ex: Ex ) {
      def append( that: Ex )( implicit tx: S#Tx ) : Ex = new BinaryOpNew( BinaryOp.Append, ex, that, tx )
      def prepend( that: Ex )( implicit tx: S#Tx ) : Ex = new BinaryOpNew( BinaryOp.Prepend, ex, that, tx )
      def reverse( implicit tx: S#Tx ) : Ex = new UnaryOpNew( UnaryOp.Reverse, ex, tx )
      def toUpperCase( implicit tx: S#Tx ) : Ex = new UnaryOpNew( UnaryOp.Upper, ex, tx )
   }

   private def change( before: String, now: String ) : Option[ Change ] = new Change( before, now ).toOption

   private final class BinaryOpNew( op: BinaryOp, a: Ex, b: Ex, tx0: S#Tx )
   extends NodeLike with StandaloneLike[ S, Change, Ex ]
   with LateBinding[ S, Change ] {
      protected val targets   = Invariant.Targets[ S ]( tx0 )
      def value( implicit tx: S#Tx ) = op.value( a.value, b.value )
      def writeData( out: DataOutput ) {
         out.writeShort( op.id )
      }
      def disposeData()( implicit tx: S#Tx ) {}
      def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a, b )

      def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
         (a.pull( source, update ), b.pull( source, update )) match {
            case (None, None)                => None
            case (Some( ach ), None )        =>
               val bv = b.value
               change( op.value( ach.before, bv ), op.value( ach.now, bv ))
            case (None, Some( bch ))         =>
               val av = a.value
               change( op.value( av, bch.before ), op.value( av, bch.now ))
            case (Some( ach ), Some( bch ))  =>
               change( op.value( ach.before, bch.before ), op.value( ach.now, bch.now ))
         }
      }
   }

   private final class UnaryOpNew( op: UnaryOp, a: Ex, tx0: S#Tx )
   extends NodeLike with StandaloneLike[ S, Change, Ex ]
   with LateBinding[ S, Change ] {
      protected val targets   = Invariant.Targets[ S ]( tx0 )
      def value( implicit tx: S#Tx ) = op.value( a.value )
      def writeData( out: DataOutput ) {
         out.writeShort( op.id )
      }
      def disposeData()( implicit tx: S#Tx ) {}
      def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a )

      def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
         a.pull( source, update ).flatMap { ach =>
            change( op.value( ach.before ), op.value( ach.now ))
         }
      }
   }

   object UnaryOp {
      def apply( id: Int ) : UnaryOp = (id: @switch) match {
         case 0 => Reverse
         case 1 => Upper
      }

      object Reverse extends UnaryOp {
         val id = 0
         def value( in: String ) = in.reverse
      }

      object Upper extends UnaryOp {
         val id = 1
         def value( in: String ) = in.toUpperCase
      }
   }
   sealed trait UnaryOp {
      def value( in: String ) : String
      def id: Int
   }

   object BinaryOp {
      def apply( id: Int ) : BinaryOp = (id: @switch) match {
         case 0 => Append
         case 1 => Prepend
      }

      object Append extends BinaryOp {
         val id = 0
         def value( a: String, b: String ) = a + b
      }

      object Prepend extends BinaryOp {
         val id = 1
         def value( a: String, b: String ) = b + a
      }
   }
   sealed trait BinaryOp {
      def value( a: String, b: String ) : String
      def id: Int
   }

   implicit def ops[ A <% Ex ]( ex: A ) : Ops = new Ops( ex )
}

object StringsTests extends App {
   new StringTests( InMemory() )
}

class StringTests[ S <: Sys[ S ]]( system: S ) {
   val strings = new Strings[ S ]
   import strings._
   import system.{ atomic => ◊ }

   val s    = ◊ { implicit tx => Var( "hallo" )}
   val s1   = ◊ { implicit tx => s.append( "-welt" )}
   val s2   = ◊ { implicit tx => s1.reverse }
   val eval = ◊ { implicit tx => s2.value }

   println( "Evaluated: " + eval )

   ◊ { implicit tx => s2.react { (tx, ch) =>
      println( "Observed: " + ch )
   }}

   ◊ { implicit tx => s.set( "kristall".reverse )}
}

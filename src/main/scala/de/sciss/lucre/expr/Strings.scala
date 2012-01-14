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
import annotation.switch
import stm.impl.InMemory
import event.Invariant

object Strings {
   def apply[ S <: Sys[ S ]] : Strings[ S ] = new Strings[ S ]
}

final class Strings[ S <: Sys[ S ]] private() extends Type[ S, String ] {
   protected def writeValue( v: String, out: DataOutput ) { out.writeString( v )}
   protected def readValue( in: DataInput ) : String = in.readString()
//   type Ops = StringOps

   // for a stupid reason scalac doesn't eat A <% Ex
   implicit def stringOps[ A <% Expr[ S, String ]]( ex: A ) : StringOps = new StringOps( ex )

//   protected def extensions: Extensions[ String ] = Strings

   final class StringOps private[Strings]( ex: Ex ) {
      def append( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Append( ex, that )
      def prepend( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Prepend( ex, that )
      def reverse( implicit tx: S#Tx ) : Ex = UnaryOp.Reverse( ex )
      def toUpperCase( implicit tx: S#Tx ) : Ex = UnaryOp.Upper( ex )
   }

   protected def readLiteral( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex =
      sys.error( "Strings doesn't define a literal type" )

   protected def unaryOp( id: Int ) = UnaryOp( id )

   private object UnaryOp {
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

   protected def binaryOp( id: Int ) = BinaryOp( id )

   private object BinaryOp {
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
}

object StringsTests extends App {
   new StringTests( InMemory() )
}

class StringTests[ S <: Sys[ S ]]( system: S ) {
   val strings = Strings[ S ]
   import strings._
   import system.{ atomic => ◊ }

   val s    = ◊ { implicit tx => Var( "hallo" )}
   val s1   = ◊ { implicit tx => s.append( "-welt" )}
   val s2   = ◊ { implicit tx => s1.reverse }
   val eval = ◊ { implicit tx => s2.value }

   println( "Evaluated: " + eval )

   ◊ { implicit tx => s2.changed.react { (tx, ch) =>
      println( "Observed: " + ch )
   }}

   ◊ { implicit tx => s.set( "kristall".reverse )}
}

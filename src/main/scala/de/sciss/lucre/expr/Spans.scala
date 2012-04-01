/*
 *  Spans.scala
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
import event.Targets

//final case class Span[ S <: Sys[ S ]]( start: Expr[ S, Long ], stop: Expr[ S, Long ])
final case class Span( start: Long, stop: Long ) {
   def length: Long = stop - start

   // where overlapping results in negative spacing
   def spacing( b: Span ) : Long = {
      val bStart = b.start
      if( start < bStart ) {
         bStart - stop
      } else {
         start - b.stop
      }
   }

   /**
    *  Checks if a position lies within the span.
    *
    *  @return		<code>true</code>, if <code>start <= pos < stop</code>
    */
   def contains( pos: Long ) : Boolean = pos >= start && pos < stop

   /**
    *  Checks if another span lies within the span.
    *
    *	@param	aSpan	second span, may be <code>null</code> (in this case returns <code>false</code>)
    *  @return		<code>true</code>, if <code>aSpan.start >= this.span &&
    *				aSpan.stop <= this.stop</code>
    */
    def contains( aSpan: Span ) : Boolean =
         (aSpan.start >= this.start) && (aSpan.stop <= this.stop)

   /**
    *  Checks if a two spans overlap each other.
    *
    *	@param	aSpan	second span
    *  @return		<code>true</code>, if the spans
    *				overlap each other
    */
    def overlaps( aSpan: Span ) : Boolean =
      ((aSpan.start < this.stop) && (aSpan.stop > this.start))

   /**
    *  Checks if a two spans overlap or touch each other.
    *
    *	@param	aSpan	second span
    *  @return		<code>true</code>, if the spans
    *				overlap each other
    */
    def touches( aSpan: Span ) : Boolean =
      if( start <= aSpan.start ) {
         stop >= aSpan.start
      } else {
         aSpan.stop >= start
      }

   /**
    *  Checks if the span is empty.
    *
    *  @return		<code>true</code>, if <code>start == stop</code>
    */
   def isEmpty : Boolean = start == stop

   def nonEmpty : Boolean = start != stop

   def unite( aSpan: Span )      = Span( math.min( start, aSpan.start ), math.max( stop, aSpan.stop ))
   def intersect( aSpan: Span )  = Span( math.max( start, aSpan.start ), math.min( stop, aSpan.stop ))

   def clip( pos: Long ) : Long = math.max( start, math.min( stop, pos ))

   def shift( delta: Long ) = Span( start + delta, stop + delta )
}

object Spans {
   def apply[ S <: Sys[ S ]]( longs: Longs[ S ]) /* ( implicit tx: S#Tx ) */ : Spans[ S ] =
      new Spans[ S ]( longs )
}

final class Spans[ S <: Sys[ S ]] private( longs: Longs[ S ]) extends TypeOld[ S, Span ] {
   tpe =>

   val id = 100

   private type LongEx = Expr[ S, Long ]

   def init()( implicit tx: S#Tx ) {
      implicit val itx = tx.peer
      // 'Span'
      longs.addExtension( this, LongExtensions )
   }

   private object LongExtensions extends TupleReader[ S, Long ] {
      def readTuple( arity: Int, opID: Int, in: DataInput, access: S#Acc, targets: Targets[ S ])
                   ( implicit tx: S#Tx ) : Expr[ S, Long ] = {
         if( arity == 1 ) {
            UnaryLongOp( opID ).read( in, access, targets )
         } else {
            sys.error( "Unknown tuple of arity " + arity + " and op-id " + opID )
         }
      }
   }

   implicit def spanOps[ A <% Expr[ S, Span ]]( ex: A ) : SpanOps = new SpanOps( ex )

//   protected def extensions: Extensions[ Span ] = Spans

   final class SpanOps private[Spans]( ex: Ex ) {
      // binary ops
      def unite( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Union( ex, that )
      def intersect( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Intersection( ex, that )

      def start_#(  implicit tx: S#Tx ) : LongEx = UnaryLongOp.Start( ex )
      def stop_#(   implicit tx: S#Tx ) : LongEx = UnaryLongOp.Stop( ex )
      def length_#( implicit tx: S#Tx ) : LongEx = UnaryLongOp.Length( ex )

//      // decomposition
//      def start( implicit tx: S#Tx ) : LongEx = ex match {
//// PPP
////         case i: Literal   => i.start
//         case _            => new UnaryLongNew( UnaryLongOp.Start, ex, tx )
//      }
//
//      def stop( implicit tx: S#Tx ) : LongEx = ex match {
//// PPP
////         case i: Literal   => i.stop
//         case _            => new UnaryLongNew( UnaryLongOp.Stop, ex, tx )
//      }
   }

   private object Literal extends Tuple2Op[ Long, Long ] {
      def value( start: Long, stop: Long ) = new expr.Span( start, stop )
      val id = 0
      def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Ex = {
         val start   = longs.readExpr( in, access )
         val stop    = longs.readExpr( in, access )
         new Tuple2( tpe.id, this, targets, start, stop )
      }

      def toString( start: LongEx, stop: LongEx ) = "Span(" + start + ", " + stop + ")"
   }

   def Span[ T1, T2 ]( start: T1, stop: T2 )( implicit tx: S#Tx, startView: T1 => LongEx, stopView: T2 => LongEx ) : Ex = {
      val targets = Targets[ S ]
      new Tuple2( tpe.id, Literal, targets, start, stop )
   }

   def readTuple( arity: Int, opID: Int, in: DataInput, access: S#Acc,
                  targets: Targets[ S ])( implicit tx: S#Tx ) : Ex = {
      (arity /*: @switch */) match {
//         case 1 => UnaryOp( opID ).read( in, access, targets )
         case 2 => {
            if( opID == 0 ) { // Literal
               Literal.read( in, access, targets )
            } else {
               BinaryOp( opID ).read( in, access, targets )
            }
         }
      }
   }

   private object BinaryOp {
      def apply( id: Int ) : BinaryOp = (id: @switch) match {
         case 1 => Union
         case 2 => Intersection
      }

      sealed trait Basic extends BinaryOp {
         final def apply( _1: Ex, _2: Ex )( implicit tx: S#Tx ) : Ex =
            new Tuple2( tpe.id, this, Targets[ S ], _1, _2 )

         def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : Ex = {
            val _1 = readExpr( in, access )
            val _2 = readExpr( in, access )
            new Tuple2( tpe.id, this, targets, _1, _2 )
         }
      }

      object Union extends Basic {
         val id = 1
         def value( a: Span, b: Span ) = a.unite( b )
         def toString( _1: Ex, _2: Ex ) = "union(" + _1 + ", " + _2 + ")"
      }

      object Intersection extends Basic {
         val id = 2
         def value( a: Span, b: Span ) = a.intersect( b )
         def toString( _1: Ex, _2: Ex ) = "intersection(" + _1 + ", " + _2 + ")"
      }
   }

   protected def readValue( in: DataInput ) : expr.Span = {
      val start   = in.readLong()
      val stop    = in.readLong()
      new Span( start, stop )
   }

   protected def writeValue( v: expr.Span, out: DataOutput ) {
      out.writeLong( v.start )
      out.writeLong( v.stop )
   }

   private object UnaryLongOp {
      def apply( id: Int ) : longs.Tuple1Op[ expr.Span ] = (id: @switch) match {
         case 1000 => Start
         case 1001 => Stop
         case 1002 => Length
      }

      sealed trait Basic extends longs.Tuple1Op[ expr.Span ] {
         final def apply( _1: Ex )( implicit tx: S#Tx ) : LongEx =
            new longs.Tuple1( tpe.id, this, Targets[ S ], _1 )

         def read( in: DataInput, access: S#Acc, targets: Targets[ S ])( implicit tx: S#Tx ) : LongEx = {
            val _1 = readExpr( in, access )
            new longs.Tuple1( tpe.id, this, targets, _1 )
         }
      }

      object Start extends Basic {
         val id = 1000
         def value( a: expr.Span ) : Long = a.start
         def toString( _1: Ex ) = _1.toString + ".start"
      }
      object Stop extends Basic {
         val id = 1001
         def value( a: expr.Span ) : Long = a.stop
         def toString( _1: Ex ) = _1.toString + ".stop"
      }
      object Length extends Basic {
         val id = 1002
         def value( a: expr.Span ) : Long = a.length
         def toString( _1: Ex ) = _1.toString + ".length"
      }
   }
}
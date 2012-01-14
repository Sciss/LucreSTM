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
import collection.immutable.{IndexedSeq => IIdxSeq}
import event.{Event, Invariant, LateBinding}
import annotation.switch
import concurrent.stm.{InTxn, Txn, TxnExecutor}

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
//   private def initTx( implicit tx: InTxn ) {
//      // 'Span'
//      Longs.addExtension( 0x5370616E, LongsExtensions )
//   }
//
//   lazy val init : Unit = {
//      Txn.findCurrent match {
//         case Some( itx )  => initTx( itx )
//         case None         => TxnExecutor.defaultAtomic( initTx( _ ))
//      }
//   }
//
//   private object LongsExtensions extends Extensions.ReaderFactory[ Long ] {
//      def reader[ S <: Sys[ S ]] : Invariant.Reader[ S, Expr[ S, Long ]] = new LongsExtReader[ S ]
//   }
//
//   private final class LongsExtReader[ S <: Sys[ S ]] extends Invariant.Reader[ S, Expr[ S, Long ]] {
//      def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Expr[ S, Long ] = {
//         val opID = in.readInt()
//         sys.error( "TODO" )
//      }
//   }
}

final class Spans[ S <: Sys[ S ]]( longs: Longs[ S ]) extends Type[ S, Span ] {
//   type Span = expr.Span[ S ]

   private type LongEx = Expr[ S, Long ]

   implicit def spanOps[ A <% Expr[ S, Span ]]( ex: A ) : SpanOps = new SpanOps( ex )

//   protected def extensions: Extensions[ Span ] = Spans

   final class SpanOps private[Spans]( ex: Ex ) {
      // binary ops
      def unite( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Union( ex, that )
      def intersect( that: Ex )( implicit tx: S#Tx ) : Ex = BinaryOp.Intersection( ex, that )

      // decomposition
      def start( implicit tx: S#Tx ) : LongEx = ex match {
         case i: Impl   => i.start
         case _         => sys.error( "TODO" )
      }

      def stop( implicit tx: S#Tx ) : LongEx = ex match {
         case i: Impl   => i.stop
         case _         => sys.error( "TODO" )
      }
   }

   private sealed trait Impl extends Basic with Expr.Node[ S, expr.Span ]
   with LateBinding[ S, Change ] {
      def start: LongEx
      def stop: LongEx

      final protected def sources( implicit tx: S#Tx ) : event.Sources[ S ] = IIdxSeq( start.changed, stop.changed )

      final protected def writeData( out: DataOutput ) {
         start.write( out )
         stop.write( out )
      }

      final protected def disposeData()( implicit tx: S#Tx ) {}

      final def value( implicit tx: S#Tx ) : expr.Span = new expr.Span( start.value, stop.value )

      final def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
         val (startBefore, startNow) = start.changed.pull( source, update ) match {
            case Some( event.Change( before, now )) => (before, now)
            case None                               => val v = start.value; (v, v)
         }

         val (stopBefore, stopNow) = stop.changed.pull( source, update ) match {
            case Some( event.Change( before, now )) => (before, now)
            case None                               => val v = stop.value; (v, v)
         }

         change( new Span( startBefore, stopBefore ), new Span( startNow, stopNow ))
      }
   }

   def Span[ A, B ]( start: A, stop: B )( implicit tx: S#Tx, aView: A => LongEx, bView: B => LongEx ) : Ex = {
      val _start: LongEx = start
      val _stop:  LongEx = stop
      new Impl {
         protected val targets   = Invariant.Targets[ S ]
         val start               = _start
         val stop                = _stop
      }
   }

   protected def unaryOp( id: Int ) : UnaryOp = sys.error( "No unary operations defined" )
   protected def binaryOp( id: Int ) : BinaryOp = BinaryOp( id )

   private object BinaryOp {
      def apply( id: Int ) : BinaryOp = (id: @switch) match {
         case 0 => Union
         case 1 => Intersection
      }

      object Union extends BinaryOp {
         val id = 0
         def value( a: Span, b: Span ) = a.unite( b )
      }

      object Intersection extends BinaryOp {
         val id = 1
         def value( a: Span, b: Span ) = a.intersect( b )
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

//   private sealed trait DecompLongImpl
//   extends Basic with Expr.Node[ S, LongEx ]
//   with LateBinding[ S, Change ] {
//      protected def op: DecompLongOp
//      protected def a: Ex
//
//      final def value( implicit tx: S#Tx ) = op.value( a.value )
//      final def writeData( out: DataOutput ) {
//         out.writeUnsignedByte( 1 )
//         out.writeShort( op.id )
//         a.write( out )
//      }
//      final def disposeData()( implicit tx: S#Tx ) {}
//      final def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a.changed )
//
//      final def pull( key: Int, source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
//         a.changed.pull( source, update ).flatMap { ach =>
//            change( op.value( ach.before ), op.value( ach.now ))
//         }
//      }
//   }
}
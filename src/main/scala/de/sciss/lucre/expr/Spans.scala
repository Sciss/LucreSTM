package de.sciss.lucre
package expr

import stm.Sys
import collection.immutable.{IndexedSeq => IIdxSeq}
import event.{Event, Invariant, LateBinding}
import annotation.switch

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

final class Spans[ S <: Sys[ S ]]( longs: Longs[ S ]) extends Type[ S, Span ] {
//   type Span = expr.Span[ S ]

   private type LongEx = Expr[ S, Long ]

   private sealed trait Impl extends Basic with Expr.Node[ S, expr.Span ]
   with LateBinding[ S, Change ] {
      protected def start: LongEx
      protected def stop: LongEx

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
         protected val start     = _start
         protected val stop      = _stop
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
}
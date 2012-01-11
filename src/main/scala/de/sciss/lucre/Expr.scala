/*
 *  Expr.scala
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

import stm.{Var => _Var, Sys, Writer}
import stm.impl.InMemory
import event.{Change, Event, Invariant, LateBinding, Observer, Reactor, Source, Sources, StandaloneLike}
import annotation.switch
import collection.immutable.{IndexedSeq => IIdxSeq}

object Expr {
   trait Var[ S <: Sys[ S ], A ] extends Expr[ S, A ] with _Var[ S#Tx, Expr[ S, A ]]
   with StandaloneLike[ S, Change[ A ], Expr[ S, A ]] with LateBinding[ S, Change[ A ]]
   with Source[ S, Change[ A ], Change[ A ], Expr[ S, A ]] {
      private type Ex = Expr[ S, A ]

      protected def ref: S#Var[ Ex ]

      protected final def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( ref.get )

      protected final def writeData( out: DataOutput ) {
         ref.write( out )
      }

      protected final def disposeData()( implicit tx: S#Tx ) {
         ref.dispose()
      }

      final def get( implicit tx: S#Tx ) : Ex = ref.get
      final def set( expr: Ex )( implicit tx: S#Tx ) {
         val before = ref.get
         if( before != expr ) {
            val con = targets.isConnected
            if( con ) before -= this
            ref.set( expr )
            if( con ) {
               expr += this
               val beforev = before.value
               val exprv   = expr.value
               fire( Change( beforev, exprv ))
            }
         }
      }

      final def transform( f: Ex => Ex )( implicit tx: S#Tx ) { set( f( get ))}

      final def value( implicit tx: S#Tx ) : A = ref.get.value

      final def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change[ A ]] = {
         if( source == this ) Some( update.asInstanceOf[ Change[ A ]]) else get.pull( source, update )
      }
   }
   trait Const[ S <: Sys[ S ], A ] extends Expr[ S, A ] with event.Constant[ S ] {
      protected def constValue : A
      final def value( implicit tx: S#Tx ) : A = constValue
      final def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change[ A ]] = None
//      final def observe( fun: (S#Tx, Event.Change[ A ]) => Unit )
//                       ( implicit tx: S#Tx ) : Event.Observer[ S, Event.Change[ A ], Expr[ S, A ]] = {
//         Event.Observer[ S, Event.Change[ A ], Expr[ S, A ]]( reader, fun )
//      }
      final def +=( r: Reactor[ S ])( implicit tx: S#Tx ) {}
      final def -=( r: Reactor[ S ])( implicit tx: S#Tx ) {}
   }
}
trait Expr[ S <: Sys[ S ], A ] extends /* Event.Val[ S, A ] with */ Event[ S, Change[ A ], Expr[ S, A ]] with Writer {
   def value( implicit tx: S#Tx ) : A
}

object Strings extends App {
   val s = new Strings( InMemory() )
   s.test()
}
class Strings[ S <: Sys[ S ]]( system: S ) {
   type StringExpr   = Expr[ S, String ]
   type StringVar    = Expr.Var[ S, String ]
   type StringChange = Change[ String ]

   implicit val exprSer : event.Serializer[ S, StringExpr ] = new event.Serializer[ S, StringExpr ] {
      def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : StringExpr = {
         // 0 = var, 1 = op
         (in.readUnsignedByte(): @switch) match {
            case 0      => readVar( in, access, targets)
            case 1      => sys.error( "TODO" )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
      }

      def readConstant( in: DataInput )( implicit tx: S#Tx ) : StringExpr = new ConstLike {
         protected val constValue = in.readString()
      }
   }
//   implicit def reader : Event.Reader[ S, StringExpr, Event.Invariant.Targets[ S ]] = exprSer

   private sealed trait StringLike extends StringExpr {
//      final def observe( fun: (S#Tx, StringChange) => Unit )
//                       ( implicit tx: S#Tx ) : Event.Observer[ S, StringChange, StringExpr ] = {
//         Event.Observer[ S, StringChange, StringExpr ]( exprSer, fun )
//      }
   }

   private sealed trait NonConstLike extends StringLike {
      final protected def reader = exprSer
   }

   private sealed trait ConstLike extends StringLike with Expr.Const[ S, String ] {
      final def react( fun: (S#Tx, StringChange) => Unit )
                       ( implicit tx: S#Tx ) : Observer[ S, StringChange, StringExpr ] = {
         Observer[ S, StringChange, StringExpr ]( exprSer, fun )
      }

      final protected def writeData( out: DataOutput ) {
         out.writeString( constValue )
      }
   }

   implicit def Const( s: String ) : StringExpr = new ConstLike {
      protected val constValue = s
   }

   private sealed trait VarLike extends NonConstLike with Expr.Var[ S, String ]

   private def readVar( in: DataInput, access: S#Acc, _targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : StringVar =
      new VarLike {
         protected val targets   = _targets
         protected val ref       = tx.readVar[ StringExpr ]( id, in )
      }

   def Var( init: String )( implicit tx: S#Tx ) : StringVar = new VarLike {
      protected val targets   = Invariant.Targets[ S ]
      protected val ref       = tx.newVar[ StringExpr ]( id, init )
   }

   final class Ops( ex: StringExpr ) {
      def append( that: StringExpr )( implicit tx: S#Tx ) : StringExpr = new BinaryOpNew( BinaryOp.Append, ex, that, tx )
      def prepend( that: StringExpr )( implicit tx: S#Tx ) : StringExpr = new BinaryOpNew( BinaryOp.Prepend, ex, that, tx )
      def reverse( implicit tx: S#Tx ) : StringExpr = new UnaryOpNew( UnaryOp.Reverse, ex, tx )
      def toUpperCase( implicit tx: S#Tx ) : StringExpr = new UnaryOpNew( UnaryOp.Upper, ex, tx )
   }

   private def change( before: String, now: String ) : Option[ StringChange ] = new Change( before, now ).toOption

   private final class BinaryOpNew( op: BinaryOp, a: StringExpr, b: StringExpr, tx0: S#Tx )
   extends NonConstLike with StandaloneLike[ S, StringChange, StringExpr ]
   with LateBinding[ S, StringChange ] {
      protected val targets   = Invariant.Targets[ S ]( tx0 )
      def value( implicit tx: S#Tx ) = op.value( a.value, b.value )
      def writeData( out: DataOutput ) {
         out.writeShort( op.id )
      }
      def disposeData()( implicit tx: S#Tx ) {}
      def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a, b )

      def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ StringChange ] = {
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

   private final class UnaryOpNew( op: UnaryOp, a: StringExpr, tx0: S#Tx )
   extends NonConstLike with StandaloneLike[ S, StringChange, StringExpr ]
   with LateBinding[ S, StringChange ] {
      protected val targets   = Invariant.Targets[ S ]( tx0 )
      def value( implicit tx: S#Tx ) = op.value( a.value )
      def writeData( out: DataOutput ) {
         out.writeShort( op.id )
      }
      def disposeData()( implicit tx: S#Tx ) {}
      def sources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a )

      def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ StringChange ] = {
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

   implicit def ops[ A <% StringExpr ]( ex: A ) : Ops = new Ops( ex )

   def test() {
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
}
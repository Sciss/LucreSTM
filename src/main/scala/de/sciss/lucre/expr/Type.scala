/*
 *  Type.scala
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
import event.{Event, Observer, Invariant, Sources}
import collection.immutable.{IndexedSeq => IIdxSeq}

/**
 * IDs:
 * 0 = Byte, 1 = Short, 2 = Int, 3 = Long, 4 = Float, 5 = Double, 6 = Boolean, 7 = Char
 * 8 = String
 */
trait Type[ S <: Sys[ S ], A ] extends Extensions[ S, A ] {
   tpe =>

   type Ex     = Expr[ S, A ]
   type Var    = Expr.Var[ S, A ]
   type Change = event.Change[ A ]
//   type Ops

   def id: Int

   protected type UnaryOp  = Tuple1Op[ A ]
   protected type BinaryOp = Tuple2Op[ A, A ]

   protected def readValue( in: DataInput ) : A
   protected def writeValue( v: A, out: DataOutput ) : Unit
//   protected def unaryOp( id: Int ) : UnaryOp
//   protected def binaryOp( id: Int ) : BinaryOp

//   implicit def ops[ A <% Ex ]( ex: A ) : Ops // = new Ops( ex )

   /* protected */ /* sealed */ trait Basic extends Expr[ S, A ] {
      final protected def reader = serializer
   }

   implicit object serializer extends event.Serializer[ S, Ex ] {
      def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex = {
         // 0 = var, 1 = op
         (in.readUnsignedByte() /*: @switch */) match {
            case 0      => new VarRead( in, access, targets, tx )
            case arity  =>
               val clazz = in.readInt()
               require( clazz == tpe.id, "Unexpected expression class " + clazz )
               val opID = in.readInt()
               readTuple( arity, opID, in, access, targets )
//            case 1      => readUnaryOp( in, access, targets )
//            case 2      => readBinaryOp( in, access, targets )
//            case 30     => readLiteral( in, access, targets )
//            case 31     => readExtension( in.readInt(), in, access, targets )
//            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
      }

      def readConstant( in: DataInput )( implicit tx: S#Tx ) : Ex = new ConstRead( in )
   }

   protected def readTuple( arity: Int, opID: Int, in: DataInput, access: S#Acc,
                            targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex

//   private def readUnaryOp( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex = {
//      val op   = unaryOp( in.readShort() )
//      val _1   = readExpr( in, access )
//      new Tuple1[ A ]( op, targets, _1 )
//   }
//
//   private def readBinaryOp( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex = {
//      val op   = binaryOp( in.readShort() )
//      val _1   = readExpr( in, access )
//      val _2   = readExpr( in, access )
//      new Tuple2[ A, A ]( op, targets, _1, _2 )
//   }
//
//   protected def readLiteral( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex

   private final class ConstRead( in: DataInput ) extends ConstLike {
      protected val constValue = readValue( in )
   }

   private final class VarRead( in: DataInput, access: S#Acc, protected val targets: Invariant.Targets[ S ], tx0: S#Tx )
   extends Basic with Expr.Var[ S, A ] {
      protected val ref = tx0.readVar[ Ex ]( id, in )
   }

   /* protected */ def readExpr( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Ex = serializer.read( in, access )

   private sealed trait ConstLike extends Expr.Const[ S, A ] {
      final def react( fun: (S#Tx, Change) => Unit )
                       ( implicit tx: S#Tx ) : Observer[ S, Change, Ex ] = {
         Observer[ S, Change, Ex ]( serializer, fun )
      }

      final protected def writeData( out: DataOutput ) {
         writeValue( constValue, out )
      }
   }

   implicit def Const( init: A ) : Ex = new ConstLike {
      protected val constValue = init
   }

   def Var( init: Ex )( implicit tx: S#Tx ) : Var = new Basic with Expr.Var[ S, A ] {
      protected val targets   = Invariant.Targets[ S ]
      protected val ref       = tx.newVar[ Ex ]( id, init )
   }

   def NamedVar( name: => String, init: Ex )( implicit tx: S#Tx ) : Var = new Basic with Expr.Var[ S, A ] {
      protected val targets   = Invariant.Targets[ S ]
      protected val ref       = tx.newVar[ Ex ]( id, init )
      override def toString   = name
   }

   def readVar( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Var = {
      // XXX should go somewhere else
      val targets = Invariant.Targets.read[ S ]( in, access )
      require( in.readUnsignedByte == 0 )
      new VarRead( in, access, targets, tx )
   }

   /* protected */ def change( before: A, now: A ) : Option[ Change ] = new event.Change( before, now ).toOption

//   protected def newBinaryOp( op: BinaryOp, a: Ex, b: Ex )( implicit tx: S#Tx ) : Ex = new BinaryOpNew( op, a, b, tx )
//   protected def newUnaryOp( op: UnaryOp, a: Ex )( implicit tx: S#Tx ) : Ex = new UnaryOpNew( op, a, tx )

   /* protected */ sealed trait TupleOp extends Invariant.Reader[ S, Ex ] {
      def id: Int
   }

   /* protected */ trait Tuple1Op[ T1 ] extends TupleOp {
      final def apply( _1: Expr[ S, T1 ])( implicit tx: S#Tx ) : Ex =
         new Tuple1[ T1 ]( this, Invariant.Targets[ S ], _1 )

      def value( a: T1 ) : A

      final protected def writeTypes( out: DataOutput ) {}
   }

   final /* protected */ class Tuple1[ T1 ]( protected val op: Tuple1Op[ T1 ],
                                       protected val targets: Invariant.Targets[ S ],
                                       protected val _1: Expr[ S, T1 ])
   extends Basic with Expr.Node[ S, A ] {
//      protected def op: Tuple1Op[ T1 ]
//      protected def _1: Expr[ S, T1 ]

      private[lucre] def connect()( implicit tx: S#Tx ) {
         _1.changed ---> this
      }

      private[lucre] def disconnect()( implicit tx: S#Tx ) {
         _1.changed -/-> this
      }

      def value( implicit tx: S#Tx ) = op.value( _1.value )

      protected def writeData( out: DataOutput ) {
         out.writeUnsignedByte( 1 )
//         out.writeShort( op.id )
         out.writeInt( tpe.id )
         out.writeInt( op.id )
         _1.write( out )
      }

      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
         _1.changed.pull( source, update ).flatMap { ach =>
            change( op.value( ach.before ), op.value( ach.now ))
         }
      }

      override def toString = _1.toString + "." + op.toString
   }

   /* protected */  trait Tuple2Op[ T1, T2 ] extends TupleOp {
      final def apply( _1: Expr[ S, T1 ], _2: Expr[ S, T2 ])( implicit tx: S#Tx ) : Ex =
         new Tuple2[ T1, T2 ]( this, Invariant.Targets[ S ], _1, _2 )

      def value( a: T1, b: T2 ) : A

      final protected def writeTypes( out: DataOutput ) {}
   }

   final /* protected */  class Tuple2[ T1, T2 ]( protected val op: Tuple2Op[ T1, T2 ],
                                           protected val targets: Invariant.Targets[ S ],
                                           protected val _1: Expr[ S, T1 ], protected val _2: Expr[ S, T2 ])
   extends Basic with Expr.Node[ S, A ] {
//      protected def op: Tuple1Op[ T1 ]
//      protected def _1: Expr[ S, T1 ]

      private[lucre] def connect()( implicit tx: S#Tx ) {
         _1.changed ---> this
         _2.changed ---> this
      }

      private[lucre] def disconnect()( implicit tx: S#Tx ) {
         _1.changed -/-> this
         _2.changed -/-> this
      }

      def value( implicit tx: S#Tx ) = op.value( _1.value, _2.value )

      protected def writeData( out: DataOutput ) {
         out.writeUnsignedByte( 2 )
         out.writeInt( tpe.id )
         out.writeInt( op.id )
         _1.write( out )
         _2.write( out )
      }

      private[lucre] def pull( source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
         (_1.changed.pull( source, update ), _2.changed.pull( source, update )) match {
            case (None, None)                => None
            case (Some( ach ), None )        =>
               val bv = _2.value
               change( op.value( ach.before, bv ), op.value( ach.now, bv ))
            case (None, Some( bch ))         =>
               val av = _1.value
               change( op.value( av, bch.before ), op.value( av, bch.now ))
            case (Some( ach ), Some( bch ))  =>
               change( op.value( ach.before, bch.before ), op.value( ach.now, bch.now ))
         }
      }

      override def toString = _1.toString + "." + op.toString + "(" + _2.toString + ")"
   }

//   private sealed trait UnaryOpImpl
//   extends Basic with Expr.Node[ S, A ] // with StandaloneLike[ S, Change, Ex ]
//   /* with LateBinding[ S, Change ] */ {
//      protected def op: UnaryOp
//      protected def a: Ex
//
//      final private[lucre] def lazySources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a.changed )
//
//      final def value( implicit tx: S#Tx ) = op.value( a.value )
//      final def writeData( out: DataOutput ) {
//         out.writeUnsignedByte( 1 )
//         out.writeShort( op.id )
//         a.write( out )
//      }
////      final def disposeData()( implicit tx: S#Tx ) {}
//
//      final private[lucre] def pull( /* key: Int, */ source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
//         a.changed.pull( source, update ).flatMap { ach =>
//            change( op.value( ach.before ), op.value( ach.now ))
//         }
//      }
//   }
//
//   private final class UnaryOpNew( protected val op: UnaryOp, protected val a: Ex, tx0: S#Tx )
//   extends UnaryOpImpl {
//      protected val targets   = Invariant.Targets[ S ]( tx0 )
//   }
//
//   private final class UnaryOpRead( in: DataInput, access: S#Acc, protected val targets: Invariant.Targets[ S ], tx0: S#Tx )
//   extends UnaryOpImpl {
//      protected val op  = unaryOp( in.readShort() )
//      protected val a   = readExpr( in, access )( tx0 )
//   }
//
//   private sealed trait BinaryOpImpl
//   extends Basic with Expr.Node[ S, A ] // with StandaloneLike[ S, Change, Ex ]
//   /* with LateBinding[ S, Change ] */ {
//      protected def op: BinaryOp
//      protected def a: Ex
//      protected def b: Ex
//
//      final private[lucre] def lazySources( implicit tx: S#Tx ) : Sources[ S ] = IIdxSeq( a.changed, b.changed )
//
//      final def value( implicit tx: S#Tx ) = op.value( a.value, b.value )
//      final def writeData( out: DataOutput ) {
//         out.writeUnsignedByte( 2 )
//         out.writeShort( op.id )
//         a.write( out )
//         b.write( out )
//      }
////      final def disposeData()( implicit tx: S#Tx ) {}
//
//      final private[lucre] def pull( /* key: Int, */ source: Event[ S, _, _ ], update: Any )( implicit tx: S#Tx ) : Option[ Change ] = {
//         (a.changed.pull( source, update ), b.changed.pull( source, update )) match {
//            case (None, None)                => None
//            case (Some( ach ), None )        =>
//               val bv = b.value
//               change( op.value( ach.before, bv ), op.value( ach.now, bv ))
//            case (None, Some( bch ))         =>
//               val av = a.value
//               change( op.value( av, bch.before ), op.value( av, bch.now ))
//            case (Some( ach ), Some( bch ))  =>
//               change( op.value( ach.before, bch.before ), op.value( ach.now, bch.now ))
//         }
//      }
//   }
//
//   private final class BinaryOpNew( protected val op: BinaryOp, protected val a: Ex, protected val b: Ex, tx0: S#Tx )
//   extends BinaryOpImpl {
//      protected val targets   = Invariant.Targets[ S ]( tx0 )
//   }
//
//   private final class BinaryOpRead( in: DataInput, access: S#Acc, protected val targets: Invariant.Targets[ S ], tx0: S#Tx )
//   extends BinaryOpImpl {
//      protected val op  = binaryOp( in.readShort() )
//      protected val a   = readExpr( in, access )( tx0 )
//      protected val b   = readExpr( in, access )( tx0 )
//   }
//
//   protected trait BinaryOp {
//      def apply( a: Ex, b: Ex )( implicit tx: S#Tx ) : Ex = new BinaryOpNew( this, a, b, tx )
//      def value( a: A, b: A ) : A
//      def id: Int
//   }
//
//   protected trait UnaryOp {
//      def apply( in: Ex )( implicit tx: S#Tx ) : Ex = new UnaryOpNew( this, in, tx )
//      def value( in: A ) : A
//      def id: Int
//   }
}

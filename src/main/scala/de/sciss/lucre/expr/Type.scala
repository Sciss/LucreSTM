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
import event.{StandaloneLike, LateBinding, Event, Observer, Invariant, Sources}
import collection.immutable.{IndexedSeq => IIdxSeq}

trait Type[ S <: Sys[ S ], A ] {
   type Ex     = Expr[ S, A ]
   type Var    = Expr.Var[ S, A ]
   type Change = event.Change[ A ]
//   type Ops

   protected def readValue( in: DataInput ) : A
   protected def writeValue( v: A, out: DataOutput ) : Unit

//   implicit def ops[ A <% Ex ]( ex: A ) : Ops // = new Ops( ex )

   protected /* sealed */ trait NodeLike extends Expr[ S, A ] {
      final protected def reader = serializer
   }

   implicit object serializer extends event.Serializer[ S, Ex ] {
      def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex = {
         // 0 = var, 1 = op
         (in.readUnsignedByte(): @switch) match {
            case 0      => readVar( in, access, targets)
            case 1      => sys.error( "TODO" )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
      }

      def readVar( in: DataInput, access: S#Acc, _targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Ex =
         new NodeLike with Expr.Var[ S, A ] {
            protected val targets   = _targets
            protected val ref       = tx.readVar[ Ex ]( id, in )
         }

      def readConstant( in: DataInput )( implicit tx: S#Tx ) : Ex = new ConstLike {
         protected val constValue = readValue( in )
      }
   }

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

   def Var( init: Ex )( implicit tx: S#Tx ) : Var = new NodeLike with Expr.Var[ S, A ] {
      protected val targets   = Invariant.Targets[ S ]
      protected val ref       = tx.newVar[ Ex ]( id, init )
   }

   def NamedVar( name: => String, init: Ex )( implicit tx: S#Tx ) : Var = new NodeLike with Expr.Var[ S, A ] {
      protected val targets   = Invariant.Targets[ S ]
      protected val ref       = tx.newVar[ Ex ]( id, init )
      override def toString   = name
   }

   private def change( before: A, now: A ) : Option[ Change ] = new event.Change( before, now ).toOption

//   protected def newBinaryOp( op: BinaryOp, a: Ex, b: Ex )( implicit tx: S#Tx ) : Ex = new BinaryOpNew( op, a, b, tx )
//   protected def newUnaryOp( op: UnaryOp, a: Ex )( implicit tx: S#Tx ) : Ex = new UnaryOpNew( op, a, tx )

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

   protected trait BinaryOp {
      def apply( a: Ex, b: Ex )( implicit tx: S#Tx ) : Ex = new BinaryOpNew( this, a, b, tx )
      def value( a: A, b: A ) : A
      def id: Int
   }

   protected trait UnaryOp {
      def apply( in: Ex )( implicit tx: S#Tx ) : Ex = new UnaryOpNew( this, in, tx )
      def value( in: A ) : A
      def id: Int
   }
}

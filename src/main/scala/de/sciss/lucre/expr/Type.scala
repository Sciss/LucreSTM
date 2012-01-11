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
import event.{Observer, Invariant}

trait Type[ S <: Sys[ S ], A ] {
   type Ex     = Expr[ S, A ]
   type Var    = Expr.Var[ S, A ]
   type Change = event.Change[ A ]

   protected def readValue( in: DataInput ) : A
   protected def writeValue( v: A, out: DataOutput ) : Unit

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

   def Var( init: A )( implicit tx: S#Tx ) : Var = new NodeLike with Expr.Var[ S, A ] {
      protected val targets   = Invariant.Targets[ S ]
      protected val ref       = tx.newVar[ Ex ]( id, init )
   }
}

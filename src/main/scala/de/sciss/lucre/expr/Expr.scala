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
package expr

import stm.{Disposable, Var => _Var, Sys, Writer}
import event._
import LucreSTM.logEvent

object Expr {
   trait Node[ S <: Sys[ S ], A ] extends Expr[ S, A ] // with Invariant[ S, Change[ A ]]
   with StandaloneLike[ S, Change[ A ], Expr[ S, A ]] with InvariantSelector[ S ] {
      final def changed: Event[ S, Change[ A ], Expr[ S, A ]] = this

      final def disposeData()( implicit tx: S#Tx ) {}

      override def toString = "Expr" + id
   }

   trait Var[ S <: Sys[ S ], A ] extends Expr[ S, A ] with _Var[ S#Tx, Expr[ S, A ]]
   // with Invariant[ S, Change[ A ]]
   with StandaloneLike[ S, Change[ A ], Expr[ S, A ]] /* with LateBinding[ S, Change[ A ]] */
   with Generator[ S, Change[ A ], Change[ A ], Expr[ S, A ]] with InvariantSelector[ S ] {
      expr =>

      import de.sciss.lucre.{event => evt}

      private type Ex = Expr[ S, A ]

      final def changed: Event[ S, Change[ A ], Expr[ S, A ]] = this // changedImp

      // ---- these need to be implemented by subtypes ----
      protected def ref: S#Var[ Ex ]
      protected def reader: evt.Reader[ S, Expr[ S, A ]]

      final protected def writeData( out: DataOutput ) {
         out.writeUnsignedByte( 0 )
         ref.write( out )
      }

      final protected def disposeData()( implicit tx: S#Tx ) {
         ref.dispose()
      }

      final private[lucre] def connect()( implicit tx: S#Tx ) {
         ref.get.changed ---> this
      }
      final private[lucre] def disconnect()( implicit tx: S#Tx ) {
         ref.get.changed -/-> this
      }

      final def get( implicit tx: S#Tx ) : Ex = ref.get
      final def set( expr: Ex )( implicit tx: S#Tx ) {
         val before = ref.get
         if( before != expr ) {
            val con = targets.nonEmpty
            logEvent( this.toString + " set " + expr + " (con = " + con + ")" )
            if( con ) before.changed -/-> this
            ref.set( expr )
            if( con ) {
               expr.changed ---> this
               val beforeV = before.value
               val exprV   = expr.value
               fire( Change( beforeV, exprV ))
            }
         }
      }

      final def getFresh( implicit tx: S#Tx ) : Ex = ref.getFresh

      final def transform( f: Ex => Ex )( implicit tx: S#Tx ) { set( f( get ))}

//      final def isFresh( implicit tx: S#Tx ) : Boolean = ref.isFresh

      final def value( implicit tx: S#Tx ) : A = ref.get.value

      final private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ Change[ A ]] = {
         if( pull.parents( this /* select() */).isEmpty ) {
            pull.resolve[ Change[ A ]]
         } else {
            get.changed.pullUpdate( pull )
         }
      }

      override def toString = "Expr.Var" + id
   }
   trait Const[ S <: Sys[ S ], A ] extends Expr[ S, A ] with event.Constant[ S ] {
      final def changed = Dummy[ S, Change[ A ], Expr[ S, A ]]
      protected def constValue : A
      final def value( implicit tx: S#Tx ) : A = constValue
      override def toString = constValue.toString
   }
}

trait Expr[ S <: Sys[ S ], A ] extends Writer {
   def changed: EventLike[ S, Change[ A ], Expr[ S, A ]]
   def value( implicit tx: S#Tx ) : A

   final def observe( fun: A => Unit )( implicit tx: S#Tx ) : Disposable[ S#Tx ] =
      observeTx( _ => fun )

   final def observeTx( fun: S#Tx => A => Unit )( implicit tx: S#Tx ) : Disposable[ S#Tx ] = {
      val o = changed.reactTx { tx => change => fun( tx )( change.now )}
      fun( tx )( value )
      o
   }
}

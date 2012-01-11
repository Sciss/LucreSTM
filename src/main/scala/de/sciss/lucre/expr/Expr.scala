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

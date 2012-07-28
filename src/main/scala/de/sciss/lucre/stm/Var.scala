/*
 *  Var.scala
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
package stm

object Sink {
   def map[ Tx, A, B ]( in: Sink[ Tx, A ])( fun: B => A ) : Sink[ Tx, B ] = new Map( in, fun )

   private final class Map[ Tx, A, B ]( in: Sink[ Tx, A ], fun: B => A )
   extends Sink[ Tx, B ] {
      override def toString = "Sink.map(" + in + ")"
      def set( v: B )( implicit tx: Tx ) { in.set( fun( v ))}
//      def write( out: DataOutput ) { in.write( out )}
//      def dispose()( implicit tx: Tx ) { in.dispose() }
   }
}

sealed trait Sink[ -Tx, @specialized -A ] {
   def set( v: A )( implicit tx: Tx ) : Unit
}

object Source {
   def map[ Tx, A, B ]( in: Source[ Tx, A ])( fun: A => B ) : Source[ Tx, B ] = new Map( in, fun )

   private final class Map[ Tx, A, B ]( in: Source[ Tx, A ], fun: A => B )
   extends Source[ Tx, B ] {
      override def toString = "Source.map(" + in + ")"
      def get( implicit tx: Tx ) : B = fun( in.get )
//      def write( out: DataOutput ) { in.write( out )}
//      def dispose()( implicit tx: Tx ) { in.dispose() }
   }
}

/* sealed */
trait Source[ -Tx, @specialized +A ] /* extends Writer with Disposable[ Tx ] */ {
   def get( implicit tx: Tx ) : A
}

//sealed trait RefLike[ -Tx, A ] extends Sink[ Tx, A ] with Source[ Tx, A ]
//
//trait Var[ -Tx, @specialized A ] extends RefLike[ Tx, A ] {
//   def transform( f: A => A )( implicit tx: Tx ) : Unit
//}

trait Var[ -Tx, @specialized A ] extends Sink[ Tx, A ] with Source[ Tx, A ] with Writer with Disposable[ Tx ] {
   def transform( f: A => A )( implicit tx: Tx ) : Unit
   def isFresh( implicit tx: Tx ) : Boolean
//   def getFresh( implicit tx: Tx ) : A
}
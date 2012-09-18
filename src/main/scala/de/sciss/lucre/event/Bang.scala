/*
 *  Bang.scala
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
package event

import stm.Sys

object Bang {
   def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : Bang[ S ] = new Impl[ S ]( Targets[ S ])

   private final class Impl[ S <: Sys[ S ]]( protected val targets: Targets[ S ])
   extends Bang[ S ] with Singleton[ S ] with Root[ S, Unit /*, Bang[ S ] */] {
      protected def reader = Bang.serializer[ S ]
   }

   implicit def serializer[ S <: Sys[ S ]] : NodeSerializer[ S, Bang[ S ]] = new NodeSerializer[ S, Bang[ S ]] {
      // note: there was a strange runtime error when using an anonymous class instead. It seems that
      // scala somehow missed to execute the body, leaving targets unassigned. Perhaps a bug
      // of scalac getting confused with the apply method?
      def read( in: DataInput, access: S#Acc, _targets: Targets[ S ])( implicit tx: S#Tx ) : Bang[ S ] =
         new Impl[ S ]( _targets )
   }
}

/**
 * A simple event implementation for an imperative (trigger) event that fires "bangs" or impulses, using the
 * `Unit` type as event type parameter. The `apply` method of the companion object builds a `Bang` which also
 * implements the `Observable` trait, so that the bang can be connected to a live view (e.g. a GUI).
 */
trait Bang[ S <: Sys[ S ]] extends Trigger.Impl[ S, Unit, Unit, Bang[ S ]] with StandaloneLike[ S, Unit, Bang[ S ]] {
   /**
    * A parameterless convenience version of the `Trigger`'s `apply` method.
    */
   def apply()( implicit tx: S#Tx ) { apply( () )}

   override def toString = "Bang"
}

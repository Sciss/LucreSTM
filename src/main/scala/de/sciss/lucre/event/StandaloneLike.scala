/*
 *  StandaloneLike.scala
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

/**
 * Standalone events unite a node and one particular event.
 *
 * WARNING: the implementations of `equals` are really tricky right now. `Impl` is more specific in that
 * `ReactorSelector` checks if the compared object is another `ReactorSelector` whose reactor has the
 * same id and whose slot is the same. On the other hand `Invariant` inherits `equals` from `Reactor`
 * which checks for another reactor and then compares their ids.
 *
 * I don't know if `Reactor` still needs the `equals` implementation?
 */
trait StandaloneLike[ S <: Sys[ S ], A, Repr ] extends InvariantNode[ S, A ] with Impl[ S, A, A, Repr ] {
   final private[event] def slot = 1
   final private[event] def reactor: Node[ S, A ] = this

   final private[event] def select( slot: Int, invariant: Boolean ) : ReactorSelector[ S ] = {
      require( slot == 1, "Invalid slot " + slot )
      require( invariant, "Invalid invariant flag. Should be true" )
      this
   }
}
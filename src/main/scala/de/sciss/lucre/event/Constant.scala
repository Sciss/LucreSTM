/*
 *  Constant.scala
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
 * A constant "event" is one which doesn't actually fire. It thus arguably isn't really an event,
 * but it can be used to implement the constant type of an expression system which can use a unified
 * event approach, where the `Constant` event just acts as a dummy event. `addReactor` and `removeReactor`
 * have no-op implementations. Also `pull` in inherited from `Root`, but will always return `None`
 * as there is no way to fire this event. Implementation must provide a constant value method
 * `constValue` and implement its serialization via `writeData`.
 */
trait Constant[ S <: Sys[ S ] /*, A */] /* extends Val[ S, A ] with Root[ S, Change[ A ]] */ {
   final def write( out: DataOutput ) {
      out.writeUnsignedByte( 3 )
      writeData( out )
   }

   protected def writeData( out: DataOutput ) : Unit
}

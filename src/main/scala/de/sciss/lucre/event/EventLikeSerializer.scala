/*
 *  EventLikeSerializer.scala
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
import annotation.switch

/**
 * A trait to serialize events which can be both constants and immutable nodes.
 * An implementation mixing in this trait just needs to implement methods
 * `readConstant` to return the constant instance, and `read` with the
 * `Event.Targets` argument to return the immutable node instance.
 *
 * The constant event should mix in `Constant` which takes care of writing
 * the appropriate serialization preamble.
 */
trait EventLikeSerializer[ S <: Sys[ S ], Repr <: Writable /* Node[ S ] */]
extends Reader[ S, Repr ] with stm.Serializer[ S#Tx, S#Acc, Repr ] {
   final def write( v: Repr, out: DataOutput ) { v.write( out )}

   def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Repr = {
      (in.readUnsignedByte(): @switch) match {
         case 3 => readConstant( in )
         case 0 =>
            val targets = Targets.readIdentified[ S ]( in, access )
            read( in, access, targets )
         case 1 =>
            val targets = Targets.readIdentifiedPartial[ S ]( in, access )
            read( in, access, targets )
         case cookie => sys.error( "Unexpected cookie " + cookie )
      }
   }

   /**
    * Called by the implementation when the cookie for constant value is
    * detected in deserialization.
    *
    * @return  the constant representation of this event like type, which
    *          should mix in trait `Constant`.
    */
   def readConstant( in: DataInput )( implicit tx: S#Tx ) : Repr
}

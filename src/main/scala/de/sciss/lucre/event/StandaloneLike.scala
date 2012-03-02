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
 */
trait StandaloneLike[ S <: Sys[ S ], A, Repr ] extends Impl[ S, A, A, Repr ] with Invariant[ S, A ] {
   final private[event] def slot = 1
   final private[event] def reactor: Node[ S, A ] = this

   final protected def connectNode()( implicit tx: S#Tx ) { connect() }
   final protected def disconnectNode()( implicit tx: S#Tx ) { disconnect() }

   final private[lucre] def getEvent( key: Int ) : Event[ S, _ <: A, _ ] = this

   final private[event] def select( slot: Int ) : NodeSelector[ S, A ] = {
      require( slot == 1, "Invalid slot " + slot )
      this
   }
}
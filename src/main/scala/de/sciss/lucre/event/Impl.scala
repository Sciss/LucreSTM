/*
 *  Impl.scala
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

trait Impl[ S <: Sys[ S ], A, A1 <: A, Repr ] extends Event[ S, A1, Repr ] with InvariantSelector[ S ] {
//   protected def slot: Int
//   protected def reactor: Node[ S, A ]

//   final private[lucre] def select() : NodeSelector[ S ] = node.select( slot )

   final private[lucre] def isSource( pull: Pull[ S ]) : Boolean = pull.hasVisited( this /* select() */)

   protected def reader: Reader[ S, Repr ]

   final private[lucre] def --->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      reactor.addTarget( slot, r )
   }

   final private[lucre] def -/->( r: ExpandedSelector[ S ])( implicit tx: S#Tx ) {
      reactor.removeTarget( slot, r )
   }

   final def react( fun: A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] =
      reactTx( _ => fun )

   final def reactTx( fun: S#Tx => A1 => Unit )( implicit tx: S#Tx ) : Observer[ S, A1, Repr ] = {
      val res = Observer[ S, A1, Repr ]( reader, fun )
      res.add( this )
      res
   }
}

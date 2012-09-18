/*
 *  Decl.scala
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
import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}

/**
 * A trait to be mixed in by event dispatching companion
 * objects. It provides part of the micro DSL in clutter-free
 * definition of events.
 *
 * It should only be mixed in modules (objects). They will have
 * to declare all the supported event types by the implementing
 * trait through ordered calls to `declare`. These calls function
 * as sort of runtime type definitions, and it is crucial that
 * the order of their calls is not changed, as these calls
 * associate incremental identifiers with the declared events.
 */
trait Decl[ S <: Sys[ S ], Impl <: NodeSelector[ S, Any ]] {
   private var cnt      = 0
   private var keyMap   = Map.empty[ Class[ _ ], Int ]
   private var idMap    = Map.empty[ Int, Declaration[ _ <: Update ]]

   final private[event] def eventID[ A ]( implicit m: ClassManifest[ A ]) : Int = keyMap( m.erasure )

   final private[event] def getEvent( impl: Impl, id: Int ) : Event[ S, _ <: Update, _ ] = idMap( id ).apply( impl )

   final private[event] def events( impl: Impl ) : IIdxSeq[ Event[ S, _ <: Update, _ ]] = {
      type Elem = Event[ S, _ <: Update, _ ]
      idMap.map[ Elem, IIdxSeq[ Elem ]]( tup => {
         val dec  = tup._2
         dec( impl )
      })( breakOut )
   }

   final protected def declare[ U <: Update ]( fun: Impl => Event[ _, U, _ ])( implicit mf: ClassManifest[ U ]) {
      new Declaration[ U ]( fun )
   }

   private final class Declaration[ U <: Update ]( fun: Impl => Event[ _, U, _ ])( implicit mf: ClassManifest[ U ]) {
      val id = 1 << cnt
      cnt += 1
      keyMap += ((mf.erasure, cnt))
      idMap += ((id, this))

      def apply( impl: Impl ) : Event[ S, U, _ ] = fun( impl ).asInstanceOf[ Event[ S, U, _ ]]
   }

   type Update

   def serializer: Reader[ S, Impl ]
}
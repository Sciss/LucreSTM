/*
 *  Trigger.scala
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

object Trigger {
   trait Impl[ S <: Sys[ S ], A, Repr <: Node[ S ]]
   extends Trigger[ S, A, Repr ] with event.EventImpl[ S, A, Repr ]
   with Generator[ S, A, Repr ] {
      final def apply( update: A )( implicit tx: S#Tx ) { fire( update )}
   }

   def apply[ S <: Sys[ S ], A ]( implicit tx: S#Tx ) : Standalone[ S, A ] = new Standalone[ S, A ] {
      protected val targets = Targets[ S ]
   }

   object Standalone {
      implicit def serializer[ S <: Sys[ S ], A ] : NodeSerializer[ S, Standalone[ S, A ]] =
         new NodeSerializer[ S, Standalone[ S, A ]] {
            def read( in: DataInput, access: S#Acc, _targets: Targets[ S ])( implicit tx: S#Tx ) : Standalone[ S, A ] =
               new Standalone[ S, A ] {
                  protected val targets = _targets
               }
         }
   }
   trait Standalone[ S <: Sys[ S ], A ] extends Impl[ S, A, Standalone[ S, A ]]
   with StandaloneLike[ S, A, Standalone[ S, A ]] with Singleton[ S ] /* with EarlyBinding[ S, A ] */
   with Root[ S, A /*, Standalone[ S, A ] */ ] {
      final protected def reader: Reader[ S, Standalone[ S, A ]] = Standalone.serializer[ S, A ]
   }
}

/**
 * A `Trigger` event is one which can be publically fired. One can think of it as the
 * imperative event in EScala.
 */
trait Trigger[ S <: Sys[ S ], A, +Repr ] extends Event[ S, A, Repr ] {
   def apply( update: A )( implicit tx: S#Tx ) : Unit
}

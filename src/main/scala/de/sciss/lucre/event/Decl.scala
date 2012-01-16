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
import expr.{Span, Expr}
import stm.impl.InMemory

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
trait Decl[ Impl[ S <: Sys[ S ]]] {
   private var cnt      = 0
   private var keyMap   = Map.empty[ Class[ _ ], Int ]
//   private var idMap    = Map.empty[ Int, Declaration[ _ <: Update[ _ ]]]
   private var idMap    = Map.empty[ Int, Declaration[ _ ]]

//   def id[ U <: Update ]( clz: Class[ U ]): Int = keyMap( clz )
   private[event] def eventID[ A ]( implicit m: ClassManifest[ A ]) : Int = keyMap( m.erasure )
//   private[event] def route[ S <: Sys[ S ]]( impl: Impl[ S ], id: Int ) : Event[ S, Update, _ ] = idMap( id ).apply( impl )
   private[event] def pull[ S <: Sys[ S ]]( impl: Impl[ S ], id: Int, source: Event[ S, _, _ ],
                                            update: Any )( implicit tx: S#Tx ) : Option[ Update[ S ]]=
      idMap( id ).apply( impl ).pull( source, update ).asInstanceOf[ Option[ Update[ S ]]]   // PPP

//   private sealed trait Key[ U ] {
//      def id: Int
//      def apply[ S <: Sys[ S ]]( disp: Impl[ S ]) : Event[ S, U, _ ]
//   }

//   protected def declare[ U[ _ ] <: Update[ _ ]]( fun: Impl[ _ ] => Event[ _, U[ _ ], _ ])( implicit mf: ClassManifest[ U[ _ ]]) : Unit =
//      new Declaration[ U ]( fun )

   protected def declare[ U[ S <: Sys[ S ]] <: Update[ InMemory ]]( fun: Impl[ InMemory ] => Event[ InMemory, U[ InMemory ], _ ])( implicit mf: ClassManifest[ U ]) : Unit =
      new Declaration[ U ]( fun )

   private final class Declaration[ U[ _ ] <: Update[ _ ]]( fun: Impl[ _ ] => Event[ _, U[ _ ], _ ])( implicit mf: ClassManifest[ U[ _ ]]) {
      val id = 1 << cnt
      cnt += 1
      keyMap += ((mf.erasure, cnt))
      idMap += ((id, this.asInstanceOf[ Declaration[ _ ]]))

      def apply[ S <: Sys[ S ]]( impl: Impl[ S ]) : Event[ S, U[ S ], _ ] = fun( impl ).asInstanceOf[ Event[ S, U[ S ], _ ]]
   }

   sealed trait Update[ S <: Sys[ S ]]

   def serializer[ S <: Sys[ S ]]: Reader[ S, Impl[ S ], _ ]
}

object Test extends Decl[ Test ] {
   case class Renamed[ S <: Sys[ S ]]( r: Test[ S ], ch: Change[ String ]) extends Update[ S ]
   case class Moved[ S <: Sys[ S ]](   r: Test[ S ], ch: Change[ Span   ]) extends Update[ S ]
   case class Removed[ S <: Sys[ S ]]( r: Test[ S ]) extends Update[ S ]

   declare[ Renamed ]( _.renamed )
   declare[ Moved   ]( _.moved   )
   declare[ Removed ]( _.removed )

   implicit def serializer[ S <: Sys[ S ]] = new Invariant.Serializer[ S, Test[ S ]] {
      def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : Test[ S ] =
         sys.error( "TODO" ) // new TestRead[ S ]( in, access, targets, tx )
   }

//   private final class TestRead[ S <: Sys[ S ]]( in: DataInput, access: S#Acc,
//                                                 protected val targets: Invariant.Targets[ S ], tx0: S#Tx )
//   extends Test[ S ] {
//
//   }
}
sealed trait Test[ S <: Sys[ S ]] extends Compound[ S, Test, Test.type ] with Invariant[ S, Test.Update[ S ]]
with LateBinding[ S, Test.Update[ S ]] {
   import Test._
   def decl = Test

   def name_# : Expr[ S, String ]
   def span_# : Expr[ S, Span   ]

   def renamed = name_#.changed.map( Renamed( this, _ ))
   def moved   = span_#.changed.map( Moved(   this, _ ))
//   def changed = renamed | moved
   def removed = event[ Removed[ S ]]

//   import de.sciss.lucre.{event => evt}
//
//   implicit val tx: S#Tx = sys.error( "test" )
//   renamed.react {
//      case (tx, Test.Renamed( evt.Change( before, now ))) =>
//   }
}
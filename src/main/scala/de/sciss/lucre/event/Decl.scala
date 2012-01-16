package de.sciss.lucre
package event

import stm.Sys
import expr.{Span, Expr}

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
   private var idMap    = Map.empty[ Int, Key[ _ ]]

//   def id[ U <: Update ]( clz: Class[ U ]): Int = keyMap( clz )
   def id( clz: Class[ _ ]): Int = keyMap( clz )
   def select[ S <: Sys[ S ]]( impl: Impl[ S ], id: Int ) : Event[ S, _, _ ] = idMap( id ).apply( impl )

   private sealed trait Key[ U ] {
      def id: Int
      def apply[ S <: Sys[ S ]]( disp: Impl[ S ]) : Event[ S, U, _ ]
   }

   protected def declare[ U <: Update ]( fun: Impl[ _ ] => Event[ _, U, _ ])( implicit mf: ClassManifest[ U ]) : Unit =
      new Declaration[ U ]( fun )

   private final class Declaration[ U <: Update ]( fun: Impl[ _ ] => Event[ _, U, _ ])( implicit mf: ClassManifest[ U ])
   extends Key[ U ] {
      val id = cnt
      cnt += 1
      keyMap += ((mf.erasure, cnt))
      idMap += ((id, this))

      def apply[ S <: Sys[ S ]]( impl: Impl[ S ]) : Event[ S, U, _ ] = fun( impl ).asInstanceOf[ Event[ S, U, _ ]]
   }

   sealed trait Update

   def serializer[ S <: Sys[ S ]]: Reader[ S, Impl[ S ], _ ]
}

object Dispatch {
   final protected class EventOps[ S <: Sys[ S ], A, Repr[ ~ <: Sys[ ~ ]], B ]( d: Dispatch[ S, A, Repr ] with Node[ S, A ],
                                                                e: Event[ S, B, _ ]) {
      def map[ A1 <: A ]( fun: B => A1 )( implicit m: ClassManifest[ A1 ]) : Event[ S, A1, Repr[ S ]] =
         new Map[ S, A, Repr, B, A1 ]( d, e, fun, d.decl.id( m.erasure ))
   }

   private final class Map[ S <: Sys[ S ], A, Repr[ ~ <: Sys[ ~ ]], B, A1 <: A ](
      protected val node: Dispatch[ S, A, Repr ] with Node[ S, A ], e: Event[ S, B, _ ], fun: B => A1,
      protected val selector: Int )
   extends event.Impl[ S, A, A1, Repr[ S ]] {
      protected def reader: Reader[ S, Repr[ S ], _ ] = node.decl.serializer[ S ]
   }

   private final class Trigger[ S <: Sys[ S ], A, Repr[ ~ <: Sys[ ~ ]], A1 <: A ](
      protected val node: Dispatch[ S, A, Repr ] with Node[ S, A ], protected val selector: Int )
   extends event.Trigger.Impl[ S, A, A1, Repr[ S ]] {
      protected def reader: Reader[ S, Repr[ S ], _ ] = node.decl.serializer[ S ]
   }
}
trait Dispatch[ S <: Sys[ S ], A, Repr[ ~ <: Sys[ ~ ]]] {
   me: Node[ S, A ] =>

   import de.sciss.lucre.{event => evt}

   def decl: Decl[ Repr ]

   implicit protected def eventOps[ B ]( e: Event[ S, B, _ ]) : Dispatch.EventOps[ S, A, Repr, B ] =
      new Dispatch.EventOps( this, e )

   protected def event[ A1 <: A ]( implicit m: ClassManifest[ A1 ]) : evt.Trigger[ S, A1, Repr[ S ]] =
      new Dispatch.Trigger( this, decl.id( m.erasure ))
}

object Test extends Decl[ Test ] {
   case class Renamed( ch: Change[ String ]) extends Update
   case class Moved(   ch: Change[ Span   ]) extends Update
   case class Removed() extends Update

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
sealed trait Test[ S <: Sys[ S ]] extends Dispatch[ S, Test.Update, Test ] with Invariant[ S, Test.Update ]
with LateBinding[ S, Test.Update ] {
   import Test._
   def decl = Test

   def name_# : Expr[ S, String ]
   def span_# : Expr[ S, Span   ]

   def renamed = name_#.changed.map( Renamed( _ ))
   def moved   = span_#.changed.map( Moved(   _ ))
//   def changed = renamed | moved
   def removed = event[ Removed ]
}
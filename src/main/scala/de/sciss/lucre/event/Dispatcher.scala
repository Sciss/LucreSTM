///*
// *  Dispatcher.scala
// *  (LucreSTM)
// *
// *  Copyright (c) 2011-2012 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is free software; you can redistribute it and/or
// *  modify it under the terms of the GNU General Public License
// *  as published by the Free Software Foundation; either
// *  version 2, june 1991 of the License, or (at your option) any later version.
// *
// *  This software is distributed in the hope that it will be useful,
// *  but WITHOUT ANY WARRANTY; without even the implied warranty of
// *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *  General Public License for more details.
// *
// *  You should have received a copy of the GNU General Public
// *  License (gpl.txt) along with this software; if not, write to the Free Software
// *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.lucre
//package event
//
//import stm.Sys
//
//object Dispatcher {
//   final protected class EventOps[ S <: Sys[ S ], A, Repr, B ]( d: Dispatcher[ S, A, Repr ] with Node[ S, A ],
//                                                                e: Event[ S, B, _ ]) {
//      def map[ A1 <: A ]( fun: B => A1 ) : Event[ S, A1, Repr ] = new Map[ S, A, Repr, B, A1 ]( d, e, fun )
//   }
//
//   private final class Map[ S <: Sys[ S ], A, Repr, B, A1 <: A ](
//      protected val node: Dispatcher[ S, A, Repr ] with Node[ S, A ], e: Event[ S, B, _ ], fun: B => A1 )
//   extends event.Impl[ S, A, A1, Repr ] {
//      protected def selector: Int = {
//         println( "WARNING: Dispatcher.Map.selector -- not yet implemented" )
//         1
//      }
//      protected def reader: Reader[ S, Repr, _ ] = node.reader
//   }
//}
//trait Dispatcher[ S <: Sys[ S ], A, Repr ] {
//   me: Node[ S, A ] =>
//
//   implicit protected def eventOps[ B ]( e: Event[ S, B, _ ]) : Dispatcher.EventOps[ S, A, Repr, B ] =
//      new Dispatcher.EventOps( this, e )
//
//   final protected def event[ A1 <: A ] : Event[ S, A1, Repr ] = sys.error( "TODO" )
//
//   protected def reader: Reader[ S, Repr, _ ]
//}
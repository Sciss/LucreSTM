package de.sciss.lucre

import collection.immutable.{IndexedSeq => IIdxSeq}

import stm.Sys
import collection.mutable.{Buffer, Map => MMap}

package object event {
//   type Reactions = IIdxSeq[ () => () => Unit ]
   type Reactions = Buffer[ () => () => Unit ]
   type Visited[ S <: Sys[ S ]] = MMap[ S#ID, Int ]

//   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ Selector[ S ]]
   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ (Int, Selector[ S ])]

  /**
   * Late binding events are defined by a static number of sources. This type specifies those
   * sources, being essentially a collection of events.
   */
//   type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _, _ ]]
//   type Sources[ S <: Sys[ S ]] = IIdxSeq[ (Event[ S, _, _ ], Int) ]

   private[lucre] type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _, _ ]]

   private val emptySeq = IIdxSeq.empty[ Nothing ]

   private[lucre] def NoSources[ S <: Sys[ S ]]  : Sources[ S ]   = emptySeq
   private[lucre] def NoChildren[ S <: Sys[ S ]] : Children[ S ]  = emptySeq
}
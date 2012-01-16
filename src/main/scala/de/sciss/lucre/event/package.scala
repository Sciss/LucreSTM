package de.sciss.lucre

import collection.immutable.{IndexedSeq => IIdxSeq}
import collection.mutable.{Map => MMap}

import stm.Sys

package object event {
   type Reactions = IIdxSeq[ () => () => Unit ]
   type Visited[ S <: Sys[ S ]] = MMap[ S#ID, Int ]

//   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ Selector[ S ]]
   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ (Int, Selector[ S ])]

  /**
   * Late binding events are defined by a static number of sources. This type specifies those
   * sources, being essentially a collection of events.
   */
//   type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _, _ ]]
  type Sources[ S <: Sys[ S ]] = IIdxSeq[ (Event[ S, _, _ ], Int) ]

//  def NoSources[ S <: Sys[ S ]] : Sources[ S ] = IIdxSeq.empty
}
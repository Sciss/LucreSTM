package de.sciss.lucre

import collection.immutable.{IndexedSeq => IIdxSeq}

import stm.Sys
import collection.mutable.{Buffer, Map => MMap}

package object event {
//   type Reactions = IIdxSeq[ () => () => Unit ]
   type Reaction  = () => () => Unit
   type Reactions = Buffer[ Reaction ]
//   type Visited[ S <: Sys[ S ]] = MMap[ S#ID, Int ]
   type Sources[ S <: Sys[ S ]] = Set[ ReactorSelector[ S ]]
   type Visited[ S <: Sys[ S ]] = MMap[ ReactorSelector[ S ], Sources[ S ]]
//   type Path[ S <: Sys[ S ]] = List[ ReactorSelector[ S ]]
   type Pull[ A ] = Option[ A ] // List[ A ]
   val EmptyPull = None // Nil
   def Pull[ A ]( update: A ) : Pull[ A ] = Some( update ) //  update :: Nil

//   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ Selector[ S ]]
   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ (Int, Selector[ S ])]

  /**
   * Late binding events are defined by a static number of sources. This type specifies those
   * sources, being essentially a collection of events.
   */
//   type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _, _ ]]
//   type Sources[ S <: Sys[ S ]] = IIdxSeq[ (Event[ S, _, _ ], Int) ]

//   private[lucre] type Sources[ S <: Sys[ S ]] = IIdxSeq[ Event[ S, _, _ ]]

   private val emptySeq = IIdxSeq.empty[ Nothing ]
   private val emptySet = Set.empty[ Nothing ]

//   private[lucre] def NoSources[ S <: Sys[ S ]]  : Sources[ S ]   = emptySeq
   private[lucre] def NoChildren[ S <: Sys[ S ]] : Children[ S ]  = emptySeq
   private[lucre] def NoSources[ S <: Sys[ S ]]  : Sources[ S ]   = emptySet.asInstanceOf[ Sources[ S ]]
}
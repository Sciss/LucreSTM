package de.sciss.lucre

import collection.immutable.{IndexedSeq => IIdxSeq}

import stm.Sys

package object event {
   type Reaction  = () => () => Unit
   private[event] type Children[ S <: Sys[ S ]] = IIdxSeq[ (Int, Selector[ S ])]

   private val emptySeq = IIdxSeq.empty[ Nothing ]

//   private[lucre] def NoSources[ S <: Sys[ S ]]  : Sources[ S ]   = emptySeq
   private[lucre] def NoChildren[ S <: Sys[ S ]] : Children[ S ]  = emptySeq
}
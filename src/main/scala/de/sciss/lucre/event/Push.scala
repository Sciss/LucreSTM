/*
 *  Push.scala
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
import collection.immutable.{IndexedSeq => IIdxSeq}

object Push {
   private[event] def apply[ S <: Sys[ S ], A ]( source: NodeSelector[ S ], update: Any )( implicit tx: S#Tx ) {
      val push    = new Impl( source, update )
      val inlet   = source.inlet
      source.reactor.children.foreach { tup =>
         val inlet2 = tup._1
         if( inlet2 == inlet ) {
            val sel = tup._2
            sel.pushUpdate( source, push )
         }
      }
      push.pull()
   }

   private val NoReactions = IIdxSeq.empty[ Reaction ]
   private val emptySet = Set.empty[ Nothing ]
//   private val emptyMap = Map.empty[ Nothing, Nothing ]
   type Parents[ S <: Sys[ S ]] = Set[ ReactorSelector[ S ]]
   private def NoParents[ S <: Sys[ S ]] : Parents[ S ] = emptySet.asInstanceOf[ Parents[ S ]]
   private def NoMutating[ S <: Sys[ S ]] : Set[ MutatingSelector[ S ]] = emptySet.asInstanceOf[ Set[ MutatingSelector[ S ]]]
   private type Visited[ S <: Sys[ S ]] = Map[ ReactorSelector[ S ], Parents[ S ]]
//   private def EmptyVisited[ S <: Sys[ S ]] : Visited[ S ] = emptyMap.asInstanceOf[ Visited[ S ]]

   private final class Impl[ S <: Sys[ S ]]( source: ReactorSelector[ S ], val update: Any )( implicit tx: S#Tx )
   extends Push[ S ] {
      private var visited     = Map( (source, NoParents[ S ])) // EmptyVisited[ S ]
      private var reactions   = NoReactions
      private var mutating    = NoMutating[ S ]

      private def addVisited( sel: ReactorSelector[ S ], parent: ReactorSelector[ S ]) : Boolean = {
         val parents = visited.getOrElse( sel, NoParents )
         visited += ((sel, parents + parent))
         parents.isEmpty
      }

      private def visitChildren( sel: ReactorSelector[ S ]) {
         val inlet   = sel.inlet
         sel.reactor.children.foreach { tup =>
            val inlet2 = tup._1
            if( inlet2 == inlet ) {
               val selChild = tup._2
               selChild.pushUpdate( sel, this )
            }
         }
      }

      def visit( sel: InvariantSelector[ S ], parent: ReactorSelector[ S ]) {
         if( addVisited( sel, parent )) visitChildren( sel )
      }

      def visit( sel: MutatingSelector[ S ], parent: ReactorSelector[ S ]) {
         if( addVisited( sel, parent )) {
            mutating += sel
            visitChildren( sel )
         }
      }

      def hasVisited( sel: ReactorSelector[ S ]) : Boolean = visited.contains( sel )

      def parents( sel: ReactorSelector[ S ]) : Parents[ S ] = visited.getOrElse( sel, NoParents )

      def addLeaf( leaf: ObserverKey[ S ], parent: ReactorSelector[ S ]) {
         val nParent = parent.nodeOption.getOrElse( sys.error( "Orphan observer " + leaf + " - no expanded node selector" ))
         tx.reactionMap.processEvent( leaf, nParent, this )
      }

      def addReaction( r: Reaction ) { reactions :+= r }

      def pull() {
         val firstPass  =    reactions.map( _.apply() )
      /* val secondPass = */ firstPass.foreach( _.apply() )

         if( mutating.nonEmpty ) {
            println( "INVALIDATED: " + mutating.mkString( ", " ))
         }
      }

      def resolve[ A ] : Option[ A ] = Some( update.asInstanceOf[ A ])
   }
}
sealed trait Pull[ S <: Sys[ S ]] {
   def resolve[ A ]: Option[ A ]
   def update: Any
   def hasVisited( sel: ReactorSelector[ S ]) : Boolean
   def parents( sel: ReactorSelector[ S ]) : Push.Parents[ S ]
}
sealed trait Push[ S <: Sys[ S ]] extends Pull[ S ] {
   def visit( sel: InvariantSelector[ S ], parent: ReactorSelector[ S ]) : Unit
   def visit( sel: MutatingSelector[ S ],  parent: ReactorSelector[ S ]) : Unit
//   def mutatingVisit( sel: ReactorSelector[ S ], parent: ReactorSelector[ S ]) : Unit
//   def addMutation( sel: ReactorSelector[ S ]) : Unit
   def addLeaf( leaf: ObserverKey[ S ], parent: ReactorSelector[ S ]) : Unit
   def addReaction( r: Reaction ) : Unit
}
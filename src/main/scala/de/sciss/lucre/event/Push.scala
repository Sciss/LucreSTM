package de.sciss.lucre
package event

import stm.Sys
import collection.immutable.{IndexedSeq => IIdxSeq}

object Push {
   private[event] def apply[ S <: Sys[ S ], A ]( source: NodeSelector[ S ], update: Any )( implicit tx: S#Tx ) {
      val push    = new Impl( /* source, */ update )
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
   private val emptyMap = Map.empty[ Nothing, Nothing ]
   type Parents[ S <: Sys[ S ]] = Set[ ReactorSelector[ S ]]
   private def NoParents[ S <: Sys[ S ]] : Parents[ S ] = emptySet.asInstanceOf[ Parents[ S ]]
   private type Visited[ S <: Sys[ S ]] = Map[ ReactorSelector[ S ], Parents[ S ]]
   private def EmptyVisited[ S <: Sys[ S ]] : Visited[ S ] = emptyMap.asInstanceOf[ Visited[ S ]]

   private final class Impl[ S <: Sys[ S ]]( /* source: ReactorSelector[ S ], */ val update: Any )( implicit tx: S#Tx )
   extends Push[ S ] {
      private var visited     = EmptyVisited[ S ]
      private var reactions   = NoReactions

      def visit( sel: ReactorSelector[ S ], parent: ReactorSelector[ S ]) {
         val parents = visited.getOrElse( sel, NoParents )
         val inlet   = sel.inlet
         visited += ((sel, parents + parent))
         if( parents.isEmpty ) {
            sel.reactor.children.foreach { tup =>
               val inlet2 = tup._1
               if( inlet2 == inlet ) {
                  val selChild = tup._2
                  selChild.pushUpdate( sel, this )
               }
            }
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
         reactions.map( _.apply() ).foreach( _.apply() )
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
   def visit( sel: ReactorSelector[ S ], parent: ReactorSelector[ S ]) : Unit
   def addLeaf( leaf: ObserverKey[ S ], parent: ReactorSelector[ S ]) : Unit
   def addReaction( r: Reaction ) : Unit
}
package de.sciss.lucrestm.fluent

import collection.immutable.{IndexedSeq => IIdxSeq}

object Reaction2 extends App {
   type Reaction = () => Unit
   type Reactions = IIdxSeq[ Reaction ]
   type Reactors  = IIdxSeq[ Reactor ]

   trait ObservableStub {
      def sinks: Reactors
      def observerKeys: IIdxSeq[ Int ]
   }

   trait Observable extends ObservableStub {
      def addSink( r: Reactor ) : Unit
      def removeSink( r: Reactor ) : Unit
   }

   trait Reactor {
      def propagate( id: AnyRef, reactions: Reactions ) : Reactions
   }

   trait Observer[ A ] {
      def apply( value: A ) : Unit
      def remove() : Unit
   }

   trait Reader[ A ] {
      def read( b: BranchStub ) : A
   }

   final case class Observation[ A ]( reader: Reader[ A ], fun: A => Unit )

   object Txn {
      private var cnt = 0
      private var map = Map.empty[ Int, Observation[ _ ]]

      def mapSource[ A ]( value: A, observerKeys: IIdxSeq[ Int ]) : Reactions = {
         observerKeys.flatMap { key =>
            map.get( key ).map { observation =>
               () => observation.asInstanceOf[ Observation[ A ]].fun( value )
            }
         }
      }

      def addObservation[ A ]( obs: Observation[ A ]) : Int = {
         val key = cnt
         cnt += 1
         map += key -> obs
         key
      }

      def removeObservation( key: Int ) {
         map -= key
      }

      def mapBranch( b: BranchStub, observerKeys: IIdxSeq[ Int ], reactions: Reactions ) : Reactions = {
         val observations = observerKeys.flatMap( map.get( _ ))
         observations.headOption match {
            case Some( obs ) =>
               val full = obs.reader.read( b ).asInstanceOf[ AnyRef ]
               observations.map( obs2 => () => obs2.fun.asInstanceOf[ AnyRef => Unit ].apply( full ))
            case None => IIdxSeq.empty
         }
      }
   }

   trait BranchLike extends Reactor with ObservableStub

   trait BranchStub extends BranchLike {
      def propagate( id: AnyRef, reactions: Reactions ) : Reactions = {
         val reactions2 = Txn.mapBranch( this, observerKeys, reactions )
         val reactions3 = sinks.foldLeft( reactions2 ) { case (reactionsIter, sink) => sink.propagate( id, reactionsIter )}
         reactions3
      }
   }

   trait Branch[ Repr ] extends BranchLike with Observable {
      def reader: Reader[ Repr ]
      def observe( fun: Repr => Unit ) : Observer[ Repr ] = {
         val key = Txn.addObservation( Observation( reader, fun ))
         new Observer[ Repr ] {
            def apply( value: Repr ) { fun( value )}
            def remove() {
               Txn.removeObservation( key )
            }
         }
      }
   }

   object LongExpr {
      val reader: Reader[ LongExpr ] = sys.error( "-" )
   }
   trait LongExpr extends Branch[ LongExpr ] {
      def reader = LongExpr.reader
   }

   trait Source extends Observable {
      def bang() {
         val id = new AnyRef
//         sinks.foreach( _.propagate( id ))
         val reactions  = Txn.mapSource( this, observerKeys )
         val reactions2 = sinks.foldLeft( reactions ) { case (reactionsIter, sink) => sink.propagate( id, reactionsIter )}
         reactions2.foreach( _.apply() )
      }
   }
}
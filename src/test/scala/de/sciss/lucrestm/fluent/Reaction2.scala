package de.sciss.lucrestm.fluent

import collection.immutable.{IndexedSeq => IIdxSeq}

object Reaction2 extends App {
   type Reaction = () => () => Unit
   type Reactions = IIdxSeq[ Reaction ]
   type Reactors  = IIdxSeq[ Reactor ]

   trait ObservableStub {
      def sinks: Reactors
      def observerKeys: IIdxSeq[ Int ]
   }

   trait Observable extends ObservableStub {
      def addSink( r: Reactor ) : Unit
      def removeSink( r: Reactor ) : Unit
      def addObserver( id: Int ) : Unit
      def removeObserver( in: Int ) : Unit
   }

   trait Reactor {
      def propagate( id: AnyRef, reactions: Reactions ) : Reactions
   }

   trait Removable {
      def remove() : Unit
   }

   trait Observer[ A ] extends Removable {
      def apply( value: A ) : Unit
      def remove() : Unit
   }

   trait Reader[ A ] {
      def read( b: BranchStub ) : A
   }

   final case class Observation[ A, Repr <: Eval[ A ]]( reader: Reader[ Repr ], fun: (Repr, A) => Unit )

   object Txn {
      private var cnt = 0
      private var map = Map.empty[ Int, Observation[ _, _ ]]

      def mapSource[ A, Repr <: Eval[ A ]]( value: Repr, observerKeys: IIdxSeq[ Int ]) : Reactions = {
         observerKeys.flatMap { key =>
            map.get( key ).map { observation =>
               () => {
                  val eval = value.eval
                  () => observation.asInstanceOf[ Observation[ A, Repr ]].fun( value, eval )
               }
            }
         }
      }

      def addObservation[ A, Repr <: Eval[ A ]]( observable: Observable,
                                                 reader: Reader[ Repr ], fun: (Repr, A) => Unit ) : Removable = {
         val key = cnt
         cnt += 1

         val obs = Observation[ A, Repr ]( reader, fun )
         map += key -> obs

         val res = new Removable {
            def remove() {
               Txn.removeObservation( key )
            }
         }
         observable.addObserver( key )
         res
      }

      def removeObservation( key: Int ) {
         map -= key
      }

      def mapBranch( b: BranchStub, observerKeys: IIdxSeq[ Int ], reactions: Reactions ) : Reactions = {
         val observations = observerKeys.flatMap( map.get( _ ))
         observations.headOption match {
            case Some( obs ) =>
               val full = obs.reader.read( b ).asInstanceOf[ Eval[ AnyRef ]]
               val funs = observations.map( _.fun ).asInstanceOf[ IIdxSeq[ (Eval[ AnyRef ], AnyRef) => Unit ]]
               val react: Reaction = () => {
                  val eval = full.eval
                  () => funs.foreach( _.apply( full, eval ))
               }
               reactions :+ react

            case None => reactions
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

   trait Eval[ A ] { def eval: A }

   trait Branch[ A, Repr <: Eval[ A ]] extends BranchLike with Observable {
      me: Repr =>

      def reader: Reader[ Repr ]

      def observe( fun: (Repr, A) => Unit ) : Removable = {
         Txn.addObservation( this, reader, fun )
      }
   }

   object LongExpr {
      val reader: Reader[ LongExpr ] = sys.error( "-" )
   }
   trait LongExpr extends Branch[ Long, LongExpr ] with Eval[ Long ] {
      def reader = LongExpr.reader
   }

   trait Source[ A, Repr <: Eval[ A ]] extends Observable {
      me: Repr =>

      def bang() {
         val id = new AnyRef
//         sinks.foreach( _.propagate( id ))
         val reactions  = Txn.mapSource[ A, Repr ]( this: Repr, observerKeys )
         val reactions2 = sinks.foldLeft( reactions ) { case (reactionsIter, sink) => sink.propagate( id, reactionsIter )}
         val evaluated = reactions2.map( _.apply() )
         evaluated.foreach( _.apply() )
      }
   }
}
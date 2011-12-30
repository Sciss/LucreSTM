package de.sciss.lucrestm.fluent

import collection.immutable.{IndexedSeq => IIdxSeq}

object Reaction2 extends App {
   args.headOption match {
      case Some( "--test2" ) => test2()
      case _                 => test1()
   }

   type Reaction = () => () => Unit
   type Reactions = IIdxSeq[ Reaction ]
   type Reactors  = IIdxSeq[ Reactor ]

   implicit def seqCanRemove[ A ]( xs: IIdxSeq[ A ]) = new SeqCanRemove( xs )

   final class SeqCanRemove[ A ]( xs: IIdxSeq[ A ]) {
      def -( elem: A ) : IIdxSeq[ A ] = {
         val idx = xs.indexOf( elem )
         if( idx < 0 ) xs else {
            xs.patch( idx, IIdxSeq.empty, 1 )
         }
      }
   }

   trait ObservableStub {
//      def sinks: Reactors
//      def observerKeys: IIdxSeq[ Int ]
      final var sinks: Reactors = IIdxSeq.empty
      final var observerKeys = IIdxSeq.empty[ Int ]
   }

   trait Observable extends ObservableStub {
      final def addSink( r: Reactor ) { sinks :+= r }
      final def removeSink( r: Reactor ) { sinks -= r }
      final def addObserver( id: Int ) { observerKeys :+= id }
      final def removeObserver( id: Int ) { observerKeys -= id }
   }

   trait Reactor {
      def propagate( /* id: AnyRef, */ reactions: Reactions ) : Reactions
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
      final def propagate( id: AnyRef, reactions: Reactions ) : Reactions = {
         val reactions2 = Txn.mapBranch( this, observerKeys, reactions )
         val reactions3 = sinks.foldLeft( reactions2 ) { case (reactionsIter, sink) => sink.propagate( /* id, */ reactionsIter )}
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
      val reader: Reader[ LongExpr ] = new Reader[ LongExpr ] {
         def read( b: BranchStub ) : LongExpr = {
            sys.error( "TODO" )
         }
      }

      def const( value: Long ) : LongExpr = new Const( value )

      def vari( init: LongExpr ) : LongExpr with Source[ Long, LongExpr ] = new Vari( init )

      private final case class Const( eval: Long ) extends ConstLongExpr {
      }

      private final class Vari( var value: LongExpr ) extends VariLongExpr with Source[ Long, LongExpr ] {
         def eval = value.eval
         def set( v: LongExpr ) {
            value = v
            bang()
         }
      }
   }
   trait LongExpr extends Eval[ Long ] {
      def reader = LongExpr.reader
   }

   trait ConstLongExpr extends LongExpr {

   }

   trait VariLongExpr extends Branch[ Long, LongExpr ] with LongExpr {

   }

   trait Source[ A, Repr <: Eval[ A ]] extends Observable {
      me: Repr =>

      def set( value: Repr ) : Unit

      protected def bang() {
//         val id = new AnyRef
         val reactions  = propagate( /* id, */ IIdxSeq.empty )
         val evaluated  = reactions.map( _.apply() )
         evaluated.foreach( _.apply() )
      }

      final def propagate( /* id: AnyRef, */ reactions: Reactions ) : Reactions = {
         val reactions1 = reactions ++ Txn.mapSource[ A, Repr ]( this: Repr, observerKeys )
         sinks.foldLeft( reactions1 ) { case (reactionsIter, sink) => sink.propagate( /* id, */ reactionsIter )}
      }
   }

   def test1() {
      val a = LongExpr.vari( LongExpr.const( 1 ))
      val b = LongExpr.vari( LongExpr.const( 2 ))

   }

   def test2() {

   }
}
//package de.sciss.lucre.event
//
//import de.sciss.lucre.stm.{TxnSerializer, Sys}
//import collection.breakOut
//import collection.immutable.{IndexedSeq => IIdxSeq}
//
//final class CollectionEvent[ S <: Sys[ S ], Repr, Elem <: Node[ S, _ ], B, A1 ](
//   private[event] val reactor: Node[ S, Repr ], elemEvt: Elem => Event[ S, B, Elem ], fun: IIdxSeq[ B ] => A1 )
//( implicit elemSer: TxnSerializer[ S#Tx, S#Acc, Elem ], protected val m: ClassManifest[ A1 ])
//extends EventImpl[ S, Repr, Repr, A1 ] with InvariantEvent[ S, A1, Repr ] {
//
//   private[lucre] def connect()( implicit tx: S#Tx ) {}
//   private[lucre] def disconnect()( implicit tx: S#Tx ) {}
//
//   def +=( elem: Elem )( implicit tx: S#Tx ) {
//      elemEvt( elem ) ---> this
//      tx._writeUgly( reactor.id, elem.id, elem )
//   }
//
//   def -=( elem: Elem )( implicit tx: S#Tx ) {
//      elemEvt( elem ) -/-> this
//   }
//
//   protected def prefix = reactor.toString + ".event"
//
//   private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A1 ] = {
//      val elems: IIdxSeq[ B ] = pull.parents( this /* select() */).flatMap( sel =>
//         sel.nodeSelectorOption match {
//            case Some( nodeSel ) => // this happens for mem-cached and not persisting systems (e.g. `InMemory`)
//               nodeSel.pullUpdate( pull ).asInstanceOf[ Option[ B ]]
//            case _ =>
//               // this happens for a persisting system (e.g. `Durable`).
//               // ; although this is not type enforced (yet), we know that
//               // `Event[ _, _, Elem ]` is represented by a `NodeSelector` with
//               // its node being _represented by_ `Elem`, and thus we know that
//               // at `sel.reactor.id` indeed an `Elem` is stored. Therefore, we
//               // may safely deserialize the element with the given reader, and
//               // can then apply `elemEvt` to get the event/selector.
//               val elem = tx._readUgly[ Elem ]( reactor.id, sel.reactor.id )
//               elemEvt( elem ).pullUpdate( pull ) // we could also do elem.select( sel.slot ) but would need an additional cast
//         }
//      )( breakOut )
//
//      if( elems.isEmpty ) None else Some( fun( elems ))
//   }
//}

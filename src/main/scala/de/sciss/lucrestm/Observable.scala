package de.sciss.lucrestm

final case class Change[ @specialized A ]( before: A, now: A )

object Observable {
   def observable[ S <: Sys[ S ], @specialized A ]( v: S#Var[ A ]) : Observable[ S, Change[ A ]] with RefLike[ S#Tx, A ] =
      sys.error( "TODO" )
}

// since S#Tx must be invariant, there is no advantage using two type parameters Txn, Acc over S
trait Observable[ Txn, @specialized A ] {
   def addObserver( observer: Observer[ Txn, A ])( implicit tx: Txn ) : Unit

   def removeObserver( observer: Observer[ Txn, A ])( implicit tx: Txn ) : Unit

   protected def notifyObservers( change: A )( implicit tx: Txn ) : Unit
}

trait Observer[ -Txn, @specialized -A ] {
   def update( value: A )( implicit tx: Txn ) : Unit
}

// versus publish-subscribe


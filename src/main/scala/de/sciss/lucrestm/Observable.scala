package de.sciss.lucrestm

final case class Change[ @specialized A ]( before: A, now: A )

// since S#Tx must be invariant, there is no advantage using two type parameters Txn, Acc over S
trait Observable[ S <: Sys[ S ], @specialized A ] {
   def addObserver( o: Observer[ S#Tx, A ])( implicit tx: S#Tx, ser: TxnSerializer[ S#Tx, S#Acc, Observer[ S#Tx, A ]]) : Unit

   def removeObserver( o: Observer[ S#Tx, A ])( implicit tx: S#Tx ) : Unit

   protected def notifyObservers( change: A )( implicit tx: S#Tx ) : Unit
}

trait Observer[ -Txn, @specialized -A ] {
   def update( value: A )( implicit tx: Txn ) : Unit
}

// versus publish-subscribe


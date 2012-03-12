package de.sciss.lucre.stm

trait PersistentStoreFactory[ Txn, Repr <: PersistentStore[ Txn ]] {
   def open( name: String ) : Repr
}

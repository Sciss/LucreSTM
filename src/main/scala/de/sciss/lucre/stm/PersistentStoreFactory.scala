package de.sciss.lucre.stm

trait PersistentStoreFactory[ +Repr <: PersistentStore ] {
   def open( name: String ) : Repr
}

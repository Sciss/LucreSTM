package de.sciss.lucre.stm

trait DataStoreFactory[ +Repr <: DataStore ] {
   def open( name: String ) : Repr
}

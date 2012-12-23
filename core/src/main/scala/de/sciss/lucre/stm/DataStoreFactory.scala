package de.sciss.lucre.stm

trait DataStoreFactory[ +Repr <: DataStore ] {
   /**
    * Opens a new database within the given storage environment.
    *
    * @param name       the name of the database
    * @param overwrite  whether to overwrite (`true`) an existing database by that name if it exists, or not (`false`, default)
    * @return           the newly opened database
    */
   def open( name: String, overwrite: Boolean = false ) : Repr
}

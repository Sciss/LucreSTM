/*
 *  DataStoreFactory.scala
 *  (LucreSTM-Core)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.stm

trait DataStoreFactory[+Repr <: DataStore] {
  /**
   * Opens a new database within the given storage environment.
   *
   * @param name       the name of the database
   * @param overwrite  whether to overwrite (`true`) an existing database by that name if it exists, or not (`false`, default)
   * @return           the newly opened database
   */
  def open(name: String, overwrite: Boolean = false): Repr
}

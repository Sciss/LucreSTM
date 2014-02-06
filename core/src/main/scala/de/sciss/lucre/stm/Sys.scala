/*
 *  Sys.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package lucre
package stm

import scala.{specialized => spec}
import stm.{Var => _Var, SpecGroup => ialized}
import serial.Serializer
import language.higherKinds

/**
 * A system in LucreSTM describes a particular mode of representing values in time and of
 * persisting values to disk. The `Sys` trait contains types for variables, identifiers,
 * access paths, and transactions which are unique to each system (such as ephemeral in-memory,
 * confluently persistent etc.).
 *
 * @tparam S   the representation type of the system
 */
trait Sys[S <: Sys[S]] {
  /**
   * The variable type of the system. Variables allow transactional storage and
   * retrieval both of immutable and mutable values. Specific systems may extend
   * the minimum capabilities described by the `Var` trait.
   *
   * @tparam A   the type of the value stored in the variable
   */
  type Var[@spec(ialized) A] <: _Var[S#Tx, A]

  /**
   * The transaction type of the system.
   */
  type Tx <: Txn[S]

  /**
   * The identifier type of the system. This is an opaque type about which the
   * user only knows that it uniquely identifies and object (or an object along
   * with its access path in the confluent case). It is thus valid to assume
   * that two objects are equal if their identifiers are equal.
   */
  type ID <: Identifier[S#Tx]

  /**
   * The path access type for objects if they carry a temporal trace. This is
   * used by confluently persistent systems, while it is typically `Unit` for
   * ephemeral systems.
   */
  type Acc

  /**
   * An entry is similar to a variable in that it can be transactionally read and written.
   * However, in a confluently persistent system, a data structure can only be
   * correctly read and written, if any element in that structure was reached by
   * starting from an `Entry` access point.
   *
   * @tparam A   the type of the value stored and updated in the access
   */
  type Entry[A] <: _Var[S#Tx, A]

  /**
   * Reads the root object representing the stored data structure,
   * or provides a newly initialized one via the `init` argument,
   * if no root has been stored yet.
   */
  def root[A](init: S#Tx => A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Entry[A]

  /**
   * Closes the underlying database (if the system is durable). The STM cannot be used beyond this call.
   * An in-memory system should have a no-op implementation.
   */
  def close(): Unit
}
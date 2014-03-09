/*
 *  Sys.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package lucre
package stm

//import scala.{specialized => spec}
//import stm.{SpecGroup => ialized}
import stm.{Var => _Var}
import serial.Serializer
import language.higherKinds

/** A system in LucreSTM describes a particular mode of representing values in time and of
  * persisting values to disk. The `Sys` trait contains types for variables, identifiers,
  * access paths, and transactions which are unique to each system (such as ephemeral in-memory,
  * confluently persistent etc.).
  *
  * @tparam S   the representation type of the system
  */
trait Sys[S <: Sys[S]] {
  /** The variable type of the system. Variables allow transactional storage and
    * retrieval both of immutable and mutable values. Specific systems may extend
    * the minimum capabilities described by the `Var` trait.
    *
    * @tparam A   the type of the value stored in the variable
    */
  type Var[/* @spec(ialized) */ A] <: _Var[S#Tx, A]

  /** The transaction type of the system. */
  type Tx <: Txn[S]

  /** The identifier type of the system. This is an opaque type about which the
    * user only knows that it uniquely identifies and object (or an object along
    * with its access path in the confluent case). It is thus valid to assume
    * that two objects are equal if their identifiers are equal.
    */
  type ID <: Identifier[S#Tx]

  /** The path access type for objects if they carry a temporal trace. This is
    * used by confluently persistent systems, while it is typically `Unit` for
    * ephemeral systems.
    */
  type Acc

  /** Reads the root object representing the stored data structure,
    * or provides a newly initialized one via the `init` argument,
    * if no root has been stored yet.
    */
  def root[A](init: S#Tx => A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A]

  /** Closes the underlying database (if the system is durable). The STM cannot be used beyond this call.
    * An in-memory system should have a no-op implementation.
    */
  def close(): Unit
}
/*
 *  Var.scala
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
import serial.Writable

object Sink {
  def map[Tx, A, B](in: Sink[Tx, A])(fun: B => A): Sink[Tx, B] = new Map(in, fun)

  private final class Map[Tx, A, B](in: Sink[Tx, A], fun: B => A)
    extends Sink[Tx, B] {

    override def toString = s"Sink.map($in)"

    def update(v: B)(implicit tx: Tx): Unit = in() = fun(v)
  }
}

/** A sink is a transactional write access to a value */
trait Sink[-Tx, /* @spec(ialized) */ -A] {
  def update(v: A)(implicit tx: Tx): Unit
}

object Source {
  def map[Tx, A, B](in: Source[Tx, A])(fun: A => B): Source[Tx, B] = new Map(in, fun)

  private final class Map[Tx, A, B](in: Source[Tx, A], fun: A => B)
    extends Source[Tx, B] {

    override def toString = s"Source.map($in)"

    def apply()(implicit tx: Tx): B = fun(in())
  }
}

/** A source is a transactional read access to a value */
trait Source[-Tx, /* @spec(ialized) */ +A] {
  def apply()(implicit tx: Tx): A
}

trait LocalVar[-Tx, /* @spec(ialized) */ A] extends Sink[Tx, A] with Source[Tx, A] {
  def isInitialized(implicit tx: Tx): Boolean
}

/** A transactional variable is an identifiable cell allowing the reading and writing of values */
trait Var[-Tx, /* @spec(ialized) */ A] extends Sink[Tx, A] with Source[Tx, A] with Writable with Disposable[Tx] {
  def transform(f: A => A)(implicit tx: Tx): Unit
}

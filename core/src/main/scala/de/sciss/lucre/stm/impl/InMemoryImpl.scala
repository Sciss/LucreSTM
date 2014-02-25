/*
 *  InMemoryImpl.scala
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
package impl

import concurrent.stm.{Ref => ScalaRef, TxnExecutor, InTxn}
import scala.{specialized => spec}
import stm.{Var => _Var, SpecGroup => ialized}
import serial.{DataInput, DataOutput, Serializer}

object InMemoryImpl {
  private type S = InMemory

  def apply(): InMemory = new System

  trait Mixin[S <: InMemoryLike[S]] extends InMemoryLike[S] {
    private final val idCnt = ScalaRef(0)

    final def newID(peer: InTxn): S#ID = {
      // // since idCnt is a ScalaRef and not InMemory#Var, make sure we don't forget to mark the txn dirty!
      // dirty = true
      val res = idCnt.get(peer) + 1
      idCnt.set(res)(peer)
      new IDImpl[S](res)
    }

    final def root[A](init: S#Tx => A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Entry[A] = {
      step { implicit tx =>
        tx.newVar[A](tx.newID(), init(tx))
      }
    }

    final def close(): Unit = ()

    // ---- cursor ----

    final def step[A](fun: S#Tx => A): A = {
      TxnExecutor.defaultAtomic(itx => fun(wrap(itx))) // new TxnImpl( this, itx )))
    }

    final def position(implicit tx: S#Tx): S#Acc = ()
  }

  private def opNotSupported(name: String): Nothing = sys.error("Operation not supported: " + name)

  private final class VarImpl[S <: InMemoryLike[S], @spec(ialized) A](peer: ScalaRef[A])
    extends _Var[S#Tx, A] {

    override def toString = "Var<" + hashCode().toHexString + ">"

    def apply()(implicit tx: S#Tx): A = peer.get(tx.peer)

    def update(v: A)(implicit tx: S#Tx): Unit = {
      peer.set(v)(tx.peer)
      // tx.markDirty()
    }

    def transform(f: A => A)(implicit tx: S#Tx): Unit = {
      peer.transform(f)(tx.peer)
      // tx.markDirty()
    }

    def write(out: DataOutput): Unit = ()

    def dispose()(implicit tx: S#Tx): Unit = {
      peer.set(null.asInstanceOf[A])(tx.peer)
      // tx.markDirty()
    }
  }

  private final class IDImpl[S <: InMemoryLike[S]](val id: Int) extends InMemoryLike.ID[S] {
    def write(out: DataOutput): Unit = ()
    def dispose()(implicit tx: S#Tx): Unit = ()

    override def toString = s"<$id>"
    override def hashCode: Int = id.##

    override def equals(that: Any) = that.isInstanceOf[InMemoryLike.ID[_]] &&
      that.asInstanceOf[InMemoryLike.ID[_]].id == id
  }

  private final class TxnImpl(val system: InMemory, val peer: InTxn)
    extends TxnMixin[InMemory] {

    override def toString = s"InMemory.Txn@${hashCode.toHexString}"
  }

  trait TxnMixin[S <: InMemoryLike[S]] extends BasicTxnImpl[S] {
    _: S#Tx =>

    final def newID(): S#ID = system.newID(peer)

    final def newPartialID(): S#ID = newID()

    final def newHandle[A](value: A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] =
      new EphemeralHandle(value)

    final def newVar[A](id: S#ID, init: A)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      val peer = ScalaRef(init)
      new VarImpl(peer)
    }

    final def newLocalVar[A](init: S#Tx => A): LocalVar[S#Tx, A] = new impl.LocalVarImpl[S, A](init)

    final def newPartialVar[A](id: S#ID, init: A)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] =
      newVar(id, init)

    final def newIntVar(id: S#ID, init: Int): S#Var[Int] = {
      val peer = ScalaRef(init)
      new VarImpl(peer)
    }

    final def newBooleanVar(id: S#ID, init: Boolean): S#Var[Boolean] = {
      val peer = ScalaRef(init)
      new VarImpl(peer)
    }

    final def newLongVar(id: S#ID, init: Long): S#Var[Long] = {
      val peer = ScalaRef(init)
      new VarImpl(peer)
    }

    final def newVarArray[A](size: Int) = new Array[S#Var[A]](size)

    final def newInMemoryIDMap[A]: IdentifierMap[S#ID, S#Tx, A] =
      IdentifierMap.newInMemoryIntMap[S#ID, S#Tx, A](new IDImpl(0))(_.id)

    final def newDurableIDMap[A](implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] =
      IdentifierMap.newInMemoryIntMap[S#ID, S#Tx, A](new IDImpl(0))(_.id)

    def readVar[A](id: S#ID, in: DataInput)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      opNotSupported("readVar")
    }

    def readPartialVar[A](pid: S#ID, in: DataInput)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] =
      readVar(pid, in)

    def readBooleanVar(id: S#ID, in: DataInput): S#Var[Boolean] = {
      opNotSupported("readBooleanVar")
    }

    def readIntVar(id: S#ID, in: DataInput): S#Var[Int] = {
      opNotSupported("readIntVar")
    }

    def readLongVar(id: S#ID, in: DataInput): S#Var[Long] = {
      opNotSupported("readLongVar")
    }

    def readID(in: DataInput, acc: S#Acc): S#ID = opNotSupported("readID")

    def readPartialID(in: DataInput, acc: S#Acc): S#ID = readID(in, acc)

    def readDurableIDMap[A](in: DataInput)
                           (implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] =
      opNotSupported("readDurableIDMap")
  }

  private final class System extends Mixin[InMemory] with InMemory {
    private type S = InMemory

    override def toString = s"InMemory@${hashCode.toHexString}"

    def wrap(itx: InTxn): S#Tx = new TxnImpl(this, itx)
  }
}
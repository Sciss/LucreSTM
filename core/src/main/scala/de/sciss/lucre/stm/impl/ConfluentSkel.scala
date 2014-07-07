/*
 *  ConfluentSkel.scala
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

package de.sciss
package lucre
package stm
package impl

import collection.immutable.{IndexedSeq => Vec, IntMap}
import concurrent.stm.{InTxn, TxnExecutor}
import util.hashing.MurmurHash3
import language.implicitConversions
import serial.{DataInput, DataOutput, Serializer}

/** A simple confluent system implementation for testing purposes only. It is not really
  * transactional (atomic), nor any thread safe, nor does it have particular performance
  * guarantees. Use it exclusively for trying out if things work under confluent semantics,
  * but don't expect wonders. For a production quality system, see the separate
  * TemporalObjects project instead.
  */
trait ConfluentSkel extends Sys[ConfluentSkel] with Cursor[ConfluentSkel] {
  final type Var[A]   = ConfluentSkel.Var[A]
  final type ID       = ConfluentSkel.ID
  final type Tx       = ConfluentSkel.Txn
  final type Acc      = Vec[Int]
  // final type Entry[A] = ConfluentSkel.Var[A]

  def inPath[A]  (_path: Acc)(fun: Tx => A): A
  def fromPath[A](_path: Acc)(fun: Tx => A): A

  type I = InMemory
}

object ConfluentSkel {
  private type Acc = Vec[Int]
  private type S = ConfluentSkel

  private type M = Map[Acc, Array[Byte]]

  sealed trait ID extends Identifier[Txn] {
    private[ConfluentSkel] def seminal: Int
    private[ConfluentSkel] def path:    Acc

    final def shortString: String = path.mkString("<", ",", ">")

    override def hashCode = {
      import MurmurHash3._
      val h0  = productSeed
      val h1  = mix(h0, seminal)
      val h2  = mixLast(h1, path.##)
      finalizeHash(h2, 2)
    }

    override def equals(that: Any): Boolean =
      that.isInstanceOf[ID] && {
        val b = that.asInstanceOf[ID]
        seminal == b.seminal && path == b.path
      }
  }

  trait Txn extends stm.Txn[S] {
    // private[stm] def inMemory: InMemory#Tx
  }

  type Var[A] = stm.Var[Txn, A]

  def apply(): S = new System

  private final class System extends ConfluentSkel {
    private var cnt = 0
    private var pathVar = Vec.empty[Int]

    var storage   = IntMap.empty[M]
    val inMemory  = InMemory()

    def inMemoryTx(tx: Tx): I#Tx = tx.inMemory

    def root[A](init: S#Tx => A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] =
      step { implicit tx =>
        tx.newVar[A](tx.newID(), init(tx))
      }

    def close(): Unit = ()

    def inPath[Z](path: Acc)(block: Tx => Z): Z =
      TxnExecutor.defaultAtomic[Z] { itx =>
        val oldPath = pathVar
        try {
          pathVar = path
          block(new TxnImpl(this, itx))
        } finally {
          pathVar = oldPath
        }
      }

    def access(id: Int, acc: S#Acc)(implicit tx: Txn): (DataInput, S#Acc) = {
      var best: Array[Byte] = null
      var bestLen = 0
      val map = storage.getOrElse(id, Map.empty)
      map.foreach {
        case (path, arr) =>
          val len = path.zip(acc).segmentLength({
            case (a, b) => a == b
          }, 0)
          if (len > bestLen && len == path.size) {
            best    = arr
            bestLen = len
          }
      }
      require(best != null, "No value for path " + acc)
      val in = DataInput(best)
      (in, acc drop bestLen)
    }

    def fromPath[A](path: Acc)(fun: Tx => A): A = {
      TxnExecutor.defaultAtomic[A] { itx =>
        pathVar = path :+ pathVar.lastOption.getOrElse(-1) + 1
        fun(new TxnImpl(this, itx))
      }
    }

    // ---- cursor ----

    def step[A](fun: S#Tx => A): A = {
      TxnExecutor.defaultAtomic[A] { itx =>
        pathVar :+= pathVar.lastOption.getOrElse(-1) + 1
        fun(new TxnImpl(this, itx))
      }
    }

    def position(implicit tx: S#Tx): S#Acc = pathVar

    def newIDCnt()(implicit tx: Tx): Int = {
      val id = cnt
      cnt += 1
      id
    }

    def newID()(implicit tx: Tx): ID = {
      val id = newIDCnt()
      new IDImpl(id, pathVar.takeRight(1))
    }
  }

  private[ConfluentSkel] def opNotSupported(name: String): Nothing = sys.error("Operation not supported: " + name)

  private object IDImpl {
    def readPath(in: DataInput): Acc = {
      val sz = in.readInt()
      Vec.fill(sz)(in.readInt())
    }

    def readAndAppend(id: Int, postfix: Acc, in: DataInput): ID = {
      val path    = readPath(in)
      val newPath = path ++ postfix // accessPath.drop( com )
      new IDImpl(id, newPath)
    }

    def readAndReplace(id: Int, newPath: Acc, in: DataInput): ID = {
      readPath(in) // just ignore it
      new IDImpl(id, newPath)
    }

    def readAndUpdate(id: Int, accessPath: Acc, in: DataInput): ID = {
      val sz      = in.readInt()
      val path    = Vec.fill(sz)(in.readInt())
      val newPath = path :+ accessPath.last
      new IDImpl(id, newPath)
    }
  }

  private final class IDImpl(val seminal: Int, val path: Acc) extends ID {
    def write(out: DataOutput): Unit = {
      out.writeInt(seminal)
      out.writeInt(path.size)
      path.foreach(out.writeInt(_))
    }

    override def toString = "<" + seminal + path.mkString(" @ ", ",", ">")

    def dispose()(implicit tx: Txn) = ()
  }

  private final class IDMapImpl[A](val id: S#ID)(implicit serializer: Serializer[S#Tx, S#Acc, A])
    extends IdentifierMap[S#ID, S#Tx, A] {

    def get(id: S#ID)(implicit tx: S#Tx): Option[A] = ???
    def getOrElse(id: S#ID, default: => A)(implicit tx: S#Tx): A = ???
    def put(id: S#ID, value: A)(implicit tx: S#Tx): Unit = ???
    def contains(id: S#ID)(implicit tx: S#Tx): Boolean = ???
    def remove(id: S#ID)(implicit tx: S#Tx): Unit = ???
    def write(out: DataOutput): Unit = ???

    def dispose()(implicit tx: S#Tx): Unit = id.dispose()

    override def toString = "IdentifierMap" + id // <" + id + ">"
  }

  private final class TxnImpl(val system: System, val peer: InTxn) extends Txn with BasicTxnImpl[S] {
    lazy val inMemory: InMemory#Tx = system.inMemoryTx(this) // .wrap(peer)

    def newID(): ID = system.newID()(this)

    def newPartialID(): S#ID = ???

    override def toString = "ConfluentSkel#Tx"

    def alloc(pid: ID): ID = new IDImpl(system.newIDCnt()(this), pid.path)

    def newVar[A](pid: ID, init: A)(implicit ser: Serializer[Txn, Acc, A]): Var[A] = {
      val id  = alloc(pid)
      val res = new VarImpl[A](id, system, ser)
      res.store(init)(this)
      res
    }

    def newLocalVar[A](init: S#Tx => A): LocalVar[S#Tx, A] = new impl.LocalVarImpl[S, A](init)

    def newPartialVar[A](id: S#ID, init: A)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = ???

    def newBooleanVar(pid: ID, init: Boolean): Var[Boolean] = newVar[Boolean](pid, init)
    def newIntVar    (pid: ID, init: Int    ): Var[Int    ] = newVar[Int    ](pid, init)
    def newLongVar   (pid: ID, init: Long   ): Var[Long   ] = newVar[Long   ](pid, init)

    def newVarArray[A](size: Int) = new Array[Var[A]](size)

    def newInMemoryIDMap[A]: IdentifierMap[S#ID, S#Tx, A] = ???

    def newDurableIDMap[A](implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] =
      new IDMapImpl[A](newID())

    private def readSource(in: DataInput, pid: ID): ID = {
      val id = in.readInt()
      new IDImpl(id, pid.path)
    }

    def readVar[A](pid: ID, in: DataInput)(implicit ser: Serializer[Txn, Acc, A]): Var[A] = {
      val id = readSource(in, pid)
      new VarImpl(id, system, ser)
    }

    def readPartialVar[A](pid: ID, in: DataInput)(implicit ser: Serializer[Txn, Acc, A]): Var[A] = ???

    def readBooleanVar(pid: ID, in: DataInput): Var[Boolean] = readVar[Boolean](pid, in)
    def readIntVar    (pid: ID, in: DataInput): Var[Int    ] = readVar[Int    ](pid, in)
    def readLongVar   (pid: ID, in: DataInput): Var[Long   ] = readVar[Long   ](pid, in)

    def readID(in: DataInput, acc: Acc): ID = IDImpl.readAndAppend(in.readInt(), acc, in)

    def readPartialID(in: DataInput, aPacc: S#Acc): S#ID = ???

    def readDurableIDMap[A](in: DataInput)
                           (implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] = ???

    def newHandle[A](value: A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] = ???
  }

  private sealed trait SourceImpl[A] {
    protected def id: ID

    protected def system: System

    protected final def toString(pre: String) = pre + id + ": " +
      system.storage.getOrElse(id.seminal, Map.empty).keys.mkString(", ")

    final def update(v: A)(implicit tx: S#Tx): Unit = store(v)

    final def write(out: DataOutput): Unit = out.writeInt(id.seminal)

    protected def writeValue(v: A, out: DataOutput): Unit

    protected def readValue(in: DataInput, postfix: Acc)(implicit tx: S#Tx): A

    final def store(v: A)(implicit tx: S#Tx): Unit = {
      val out   = DataOutput()
      writeValue(v, out)
      val bytes = out.toByteArray
      system.storage += id.seminal -> (system.storage.getOrElse(id.seminal,
        Map.empty[Acc, Array[Byte]]) + (id.path -> bytes))
    }

    final def apply()(implicit tx: Txn): A = access(id.path)

    private def access(acc: S#Acc)(implicit tx: S#Tx): A = {
      val (in, acc1) = system.access(id.seminal, acc)
      readValue(in, acc1)
    }

    final def transform(f: A => A)(implicit tx: S#Tx): Unit = this() = f(this())

    final def dispose()(implicit tx: S#Tx) = ()
  }

  private final class VarImpl[A](val id: ID, val system: System, ser: Serializer[S#Tx, S#Acc, A])
    extends stm.Var[S#Tx, A] with SourceImpl[A] {

    override def toString = toString("Var")

    protected def writeValue(v: A, out: DataOutput): Unit = ser.write(v, out)

    protected def readValue(in: DataInput, postfix: S#Acc)(implicit tx: S#Tx): A = {
      ser.read(in, postfix)
    }
  }
}
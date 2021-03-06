/*
 *  DurableImpl.scala
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

import scala.annotation.meta.field
import scala.concurrent.stm.{Txn, Ref, InTxn, TxnExecutor}
import annotation.elidable
import serial.{DataInput, DataOutput, Serializer}

object DurableImpl {
  private type D[S <: DurableLike[S]] = DurableLike[S]

  def apply(store: DataStore): Durable = new System(store)

  def apply(factory: DataStoreFactory[DataStore], name: String = "data"): Durable = apply(factory.open(name))

  trait Mixin[S <: D[S], I <: Sys[I]] extends DurableLike[S] {
    system =>

    protected def store: DataStore

    @field private[this] val idCntVar = step { implicit tx =>
      val _id = store.get(_.writeInt(0))(_.readInt()).getOrElse(1)
      val _idCnt = Ref(_id)
      new CachedIntVar[S](0, _idCnt)
    }

    def root[A](init: S#Tx => A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] =
      step { implicit tx =>
        rootBody(init)
      }

    def rootJoin[A](init: S#Tx => A)(implicit tx: TxnLike, serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] =
      rootBody(init)(wrap(tx.peer), serializer)

    private[this] def rootBody[A](init: S#Tx => A)
                           (implicit tx: S#Tx, serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] = {
      val rootID = 2 // 1 == reaction map!!!
      if (exists(rootID)) {
        new VarImpl[S, A](rootID, serializer)
      } else {
        val id = newIDValue()
        require(id == rootID,
          s"Root can only be initialized on an empty database (expected id count is $rootID but found $id)")
        val res = new VarImpl[S, A](id, serializer)
        res.setInit(init(tx))
        res
      }
    }

    // ---- cursor ----

    def step[A](fun: S#Tx => A): A = {
      if (Txn.findCurrent.isDefined)
        throw new IllegalStateException("Nested transactions not supported yet by Durable system.")

      TxnExecutor.defaultAtomic(itx => fun(wrap(itx)))
    }

    def position(implicit tx: S#Tx): S#Acc = ()

    def debugListUserRecords()(implicit tx: S#Tx): Seq[ID] = {
      val b   = Seq.newBuilder[ID]
      val cnt = idCntVar()
      var i   = 1
      while (i <= cnt) {
        if (exists(i)) b += new IDImpl(i)
        i += 1
      }
      b.result()
    }

    def close(): Unit = store.close()

    def numRecords(implicit tx: S#Tx): Int = store.numEntries

    def numUserRecords(implicit tx: S#Tx): Int = math.max(0, numRecords - 1)

    // this increases a durable variable, thus ensures markDirty() already
    def newIDValue()(implicit tx: S#Tx): Int = {
      val id = idCntVar() + 1
      log(s"new   <$id>")
      idCntVar() = id
      id
    }

    def write(id: Long)(valueFun: DataOutput => Unit)(implicit tx: S#Tx): Unit = {
      log(s"writeL <$id>")
      store.put(_.writeLong(id))(valueFun)
      //         tx.markDirty()
    }

    def write(id: Int)(valueFun: DataOutput => Unit)(implicit tx: S#Tx): Unit = {
      log(s"write <$id>")
      store.put(_.writeInt(id))(valueFun)
      //         tx.markDirty()
    }

    def remove(id: Long)(implicit tx: S#Tx): Unit = {
      log(s"removL <$id>")
      store.remove(_.writeLong(id))
      //         tx.markDirty()
    }

    def remove(id: Int)(implicit tx: S#Tx): Unit = {
      log(s"remov <$id>")
      store.remove(_.writeInt(id))
      //         tx.markDirty()
    }

    def tryRead[A](id: Long)(valueFun: DataInput => A)(implicit tx: S#Tx): Option[A] = {
      log(s"readL  <$id>")
      store.get(_.writeLong(id))(valueFun)
    }

    def read[A](id: Int)(valueFun: DataInput => A)(implicit tx: S#Tx): A = {
      log(s"read  <$id>")
      store.get(_.writeInt(id))(valueFun).getOrElse(sys.error(s"Key not found $id"))
    }

    def exists(id: Int)(implicit tx: S#Tx): Boolean = store.contains(_.writeInt(id))

    def exists(id: Long)(implicit tx: S#Tx): Boolean = store.contains(_.writeLong(id))
  }

  trait TxnMixin[S <: D[S]] extends DurableLike.Txn[S] with BasicTxnImpl[S] {
    _: S#Tx =>

    final def newID(): S#ID = new IDImpl[S](system.newIDValue()(this))

    final def newPartialID(): S#ID = newID()

    final def newVar[A](id: S#ID, init: A)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      val res = new VarImpl[S, A](system.newIDValue()(this), ser)
      res.setInit(init)(this)
      res
    }

    final def newLocalVar[A](init: S#Tx => A): LocalVar[S#Tx, A] = new impl.LocalVarImpl[S, A](init)

    final def newPartialVar[A](id: S#ID, init: A)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] =
      newVar(id, init)

    final def newCachedVar[A](init: A)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      val res = new CachedVarImpl[S, A](system.newIDValue()(this), Ref(init), ser)
      res.writeInit()(this)
      res
    }

    final def newBooleanVar(id: S#ID, init: Boolean): S#Var[Boolean] = {
      val res = new BooleanVar[S](system.newIDValue()(this))
      res.setInit(init)(this)
      res
    }

    final def newIntVar(id: S#ID, init: Int): S#Var[Int] = {
      val res = new IntVar[S](system.newIDValue()(this))
      res.setInit(init)(this)
      res
    }

    final def newCachedIntVar(init: Int): S#Var[Int] = {
      val res = new CachedIntVar[S](system.newIDValue()(this), Ref(init))
      res.writeInit()(this)
      res
    }

    final def newLongVar(id: S#ID, init: Long): S#Var[Long] = {
      val res = new LongVar[S](system.newIDValue()(this))
      res.setInit(init)(this)
      res
    }

    final def newCachedLongVar(init: Long): S#Var[Long] = {
      val res = new CachedLongVar[S](system.newIDValue()(this), Ref(init))
      res.writeInit()(this)
      res
    }

    final def newVarArray[A](size: Int): Array[S#Var[A]] = new Array[Var[S#Tx, A]](size)

    final def newInMemoryIDMap[A]: IdentifierMap[S#ID, S#Tx, A] =
      IdentifierMap.newInMemoryIntMap[S#ID, S#Tx, A](new IDImpl(0))(_.id)

    final def newDurableIDMap[A](implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] =
      new IDMapImpl[S, A](newID())

    final def readVar[A](pid: S#ID, in: DataInput)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      val id = in./* PACKED */ readInt()
      new VarImpl[S, A](id, ser)
    }

    final def readPartialVar[A](pid: S#ID, in: DataInput)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] =
      readVar(pid, in)

    final def readCachedVar[A](in: DataInput)(implicit ser: Serializer[S#Tx, S#Acc, A]): S#Var[A] = {
      val id = in./* PACKED */ readInt()
      val res = new CachedVarImpl[S, A](id, Ref.make[A](), ser)
      res.readInit()(this)
      res
    }

    final def readBooleanVar(pid: S#ID, in: DataInput): S#Var[Boolean] = {
      val id = in./* PACKED */ readInt()
      new BooleanVar(id)
    }

    final def readIntVar(pid: S#ID, in: DataInput): S#Var[Int] = {
      val id = in./* PACKED */ readInt()
      new IntVar(id)
    }

    final def readCachedIntVar(in: DataInput): S#Var[Int] = {
      val id = in./* PACKED */ readInt()
      val res = new CachedIntVar[S](id, Ref(0))
      res.readInit()(this)
      res
    }

    final def readLongVar(pid: S#ID, in: DataInput): S#Var[Long] = {
      val id = in./* PACKED */ readInt()
      new LongVar(id)
    }

    final def readCachedLongVar(in: DataInput): S#Var[Long] = {
      val id = in./* PACKED */ readInt()
      val res = new CachedLongVar[S](id, Ref(0L))
      res.readInit()(this)
      res
    }

    final def readID(in: DataInput, acc: S#Acc): S#ID = {
      val base = in./* PACKED */ readInt()
      new IDImpl(base)
    }

    final def readPartialID(in: DataInput, acc: S#Acc): S#ID = readID(in, acc)

    final def readDurableIDMap[A](in: DataInput)(implicit serializer: Serializer[S#Tx, S#Acc, A]): IdentifierMap[S#ID, S#Tx, A] = {
      val base  = in./* PACKED */ readInt()
      val mapID = new IDImpl[S](base)
      new IDMapImpl[S, A](mapID)
    }

    final def newHandle[A](value: A)(implicit serializer: Serializer[S#Tx, S#Acc, A]): Source[S#Tx, A] =
      new EphemeralHandle(value)
  }

  private final class IDImpl[S <: D[S]](@field val id: Int) extends DurableLike.ID[S] {
    def write(out: DataOutput): Unit = out./* PACKED */ writeInt(id)

    override def hashCode: Int = id

    override def equals(that: Any): Boolean = that match {
      case b: IDImpl[_] => id == b.id
      case _ => false
    }

    def dispose()(implicit tx: S#Tx): Unit = tx.system.remove(id)

    override def toString = s"<$id>"
  }

  private final class IDMapImpl[S <: D[S], A](@field val id: S#ID)(implicit serializer: Serializer[S#Tx, S#Acc, A])
    extends IdentifierMap[S#ID, S#Tx, A] {
    map =>

    @field private[this] val idn = id.id.toLong << 32

    def get(id: S#ID)(implicit tx: S#Tx): Option[A] = {
      tx.system.tryRead(idn | (id.id.toLong & 0xFFFFFFFFL))(serializer.read(_, ()))
    }

    def getOrElse(id: S#ID, default: => A)(implicit tx: S#Tx): A = get(id).getOrElse(default)

    def put(id: S#ID, value: A)(implicit tx: S#Tx): Unit =
      tx.system.write(idn | (id.id.toLong & 0xFFFFFFFFL))(serializer.write(value, _))

    def contains(id: S#ID)(implicit tx: S#Tx): Boolean =
      tx.system.exists(idn | (id.id.toLong & 0xFFFFFFFFL))

    def remove(id: S#ID)(implicit tx: S#Tx): Unit =
      tx.system.remove(idn | (id.id.toLong & 0xFFFFFFFFL))

    def write(out: DataOutput): Unit = id.write(out)

    def dispose()(implicit tx: S#Tx): Unit = id.dispose()

    override def toString = s"IdentifierMap$id"
  }

  private sealed trait BasicSource[S <: D[S], A] extends Var[S#Tx, A] {
    protected def id: Int

    final def write(out: DataOutput): Unit = out./* PACKED */ writeInt(id)

    def dispose()(implicit tx: S#Tx): Unit = tx.system.remove(id)

    @elidable(elidable.CONFIG) protected final def assertExists()(implicit tx: S#Tx): Unit =
      require(tx.system.exists(id), s"trying to write disposed ref $id")
  }

  private sealed trait BasicVar[S <: D[S], A] extends BasicSource[S, A] {
    protected def ser: Serializer[S#Tx, S#Acc, A]

    final def apply()(implicit tx: S#Tx): A = tx.system.read[A](id)(ser.read(_, ()))

    final def setInit(v: A)(implicit tx: S#Tx): Unit =
      tx.system.write(id)(ser.write(v, _))
  }

  private final class VarImpl[S <: D[S], A](@field protected val id: Int,
                                            @field protected val ser: Serializer[S#Tx, S#Acc, A])
    extends BasicVar[S, A] {

    def update(v: A)(implicit tx: S#Tx): Unit = {
      assertExists()
      tx.system.write(id)(ser.write(v, _))
    }

    def transform(f: A => A)(implicit tx: S#Tx): Unit = this() = f(this())

    override def toString = s"Var($id)"
  }

  private final class CachedVarImpl[S <: D[S], A](@field protected val id: Int, peer: Ref[A],
                                                  ser: Serializer[S#Tx, S#Acc, A])
    extends BasicSource[S, A] {

    def apply()(implicit tx: S#Tx): A = peer.get(tx.peer)

    def setInit(v: A)(implicit tx: S#Tx): Unit = this() = v

    def update(v: A)(implicit tx: S#Tx): Unit = {
      peer.set(v)(tx.peer)
      tx.system.write(id)(ser.write(v, _))
    }

    def writeInit()(implicit tx: S#Tx): Unit =
      tx.system.write(id)(ser.write(this(), _))

    def readInit()(implicit tx: S#Tx): Unit =
      peer.set(tx.system.read(id)(ser.read(_, ())))(tx.peer)

    def transform(f: A => A)(implicit tx: S#Tx): Unit = this() = f(this())

    override def toString = s"Var($id)"
  }

  private final class BooleanVar[S <: D[S]](@field protected val id: Int)
    extends BasicSource[S, Boolean] {

    def apply()(implicit tx: S#Tx): Boolean =
      tx.system.read[Boolean](id)(_.readBoolean())

    def setInit(v: Boolean)(implicit tx: S#Tx): Unit =
      tx.system.write(id)(_.writeBoolean(v))

    def update(v: Boolean)(implicit tx: S#Tx): Unit = {
      assertExists()
      tx.system.write(id)(_.writeBoolean(v))
    }

    def transform(f: Boolean => Boolean)(implicit tx: S#Tx): Unit =
      this() = f(this())

    override def toString = s"Var[Boolean]($id)"
  }

  private final class IntVar[S <: D[S]](@field protected val id: Int)
    extends BasicSource[S, Int] {

    def apply()(implicit tx: S#Tx): Int =
      tx.system.read[Int](id)(_.readInt())

    def setInit(v: Int)(implicit tx: S#Tx): Unit =
      tx.system.write(id)(_.writeInt(v))

    def update(v: Int)(implicit tx: S#Tx): Unit = {
      assertExists()
      tx.system.write(id)(_.writeInt(v))
    }

    def transform(f: Int => Int)(implicit tx: S#Tx): Unit =
      this() = f(this())

    override def toString = s"Var[Int]($id)"
  }

  private final class CachedIntVar[S <: D[S]](@field protected val id: Int, peer: Ref[Int])
    extends BasicSource[S, Int] {

    def apply()(implicit tx: S#Tx): Int = peer.get(tx.peer)

    def setInit(v: Int)(implicit tx: S#Tx): Unit = this() = v

    def update(v: Int)(implicit tx: S#Tx): Unit = {
      peer.set(v)(tx.peer)
      tx.system.write(id)(_.writeInt(v))
    }

    def writeInit()(implicit tx: S#Tx): Unit =
      tx.system.write(id)(_.writeInt(this()))

    def readInit()(implicit tx: S#Tx): Unit =
      peer.set(tx.system.read(id)(_.readInt()))(tx.peer)

    def transform(f: Int => Int)(implicit tx: S#Tx): Unit = this() = f(this())

    override def toString = s"Var[Int]($id)"
  }

  private final class LongVar[S <: D[S]](@field protected val id: Int)
    extends BasicSource[S, Long] {

    def apply()(implicit tx: S#Tx): Long =
      tx.system.read[Long](id)(_.readLong())

    def setInit(v: Long)(implicit tx: S#Tx): Unit =
      tx.system.write(id)(_.writeLong(v))

    def update(v: Long)(implicit tx: S#Tx): Unit = {
      assertExists()
      tx.system.write(id)(_.writeLong(v))
    }

    def transform(f: Long => Long)(implicit tx: S#Tx): Unit =
      this() = f(this())

    override def toString = s"Var[Long]($id)"
  }

  private final class CachedLongVar[S <: D[S]](@field protected val id: Int, peer: Ref[Long])
    extends BasicSource[S, Long] {

    def apply()(implicit tx: S#Tx): Long = peer.get(tx.peer)

    def setInit(v: Long)(implicit tx: S#Tx): Unit = this() = v

    def update(v: Long)(implicit tx: S#Tx): Unit = {
      peer.set(v)(tx.peer)
      tx.system.write(id)(_.writeLong(v))
    }

    def writeInit()(implicit tx: S#Tx): Unit =
      tx.system.write(id)(_.writeLong(this()))

    def readInit()(implicit tx: S#Tx): Unit =
      peer.set(tx.system.read(id)(_.readLong()))(tx.peer)

    def transform(f: Long => Long)(implicit tx: S#Tx): Unit =
      this() = f(this())

    override def toString = s"Var[Long]($id)"
  }

  private final class TxnImpl(@field val system: System, @field val peer: InTxn)
    extends TxnMixin[Durable] with Durable.Txn {

    lazy val inMemory: InMemory#Tx = system.inMemory.wrap(peer)

    override def toString = s"Durable.Txn@${hashCode.toHexString}"
  }

  private final class System(@field protected val store: DataStore)
    extends Mixin[Durable, InMemory] with Durable {

    private type S = Durable

    @field val inMemory: InMemory = InMemory()

    def inMemoryTx(tx: Tx): I#Tx = tx.inMemory

    override def toString = s"Durable@${hashCode.toHexString}"

    def wrap(peer: InTxn): S#Tx = new TxnImpl(this, peer)
  }
}

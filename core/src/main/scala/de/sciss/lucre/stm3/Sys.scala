package de.sciss.lucre.stm3

import language.higherKinds

trait Sys[S <: Sys[S]] {
  type Tx <: Txn[S]
  type Var[A] <: de.sciss.lucre.stm3.Var[S#Tx, A]
  type Acc
}

trait Txn[S <: Sys[S]] {
  /*

  tx.alloc { f =>
    val i = f(3)
    val s = f("foo")
    new Impl(i, s)
  }

   */

  //  def readVar[A](name: String, in: DataInput, access: S#Acc)
  //                (implicit serializer: Serializer[S#Tx, S#Acc, A]): S#Var[A]
}

trait Source[-Tx, +A] {
  def apply()(implicit tx: Tx): A
}

trait Sink[-Tx, -A] {
  def update(value: A)(implicit tx: Tx): Unit
}

trait VarLike[-Tx, A] extends Source[Tx, A] with Sink[Tx, A] {
  def update(value: A)(implicit tx: Tx): Unit
}

//trait Writable {
//  def write(out: DataOutput): Unit
//}

object Var {
  implicit def serializer[Tx, Acc, A]: Serializer[Tx, Acc, Var[Tx, A]] = ???
}
trait Var[-Tx, A] extends VarLike[Tx, A] /* with Writable */

object Writer {
//  implicit object Self extends Writer[Writable] {
//    def write(value: Writable, out: DataOutput): Unit = value.write(out)
//  }
}
trait Writer[-A] {
  // def tpe: Int
  def write(value: A, out: DataOutput): Unit
}

trait Reader[-Tx, -Acc, +A] {
  // def tpe: Int
  def read(in: DataInput[Tx, Acc])(implicit tx: Tx): A

  // def instanceMethod: String  // e.g. "de.sciss.synth.proc.Proc#apply"
}

trait Writer1[-A[_]] {
  // def tpe: Int
  def write[S <: Sys[S]](value: A[S], out: DataOutput): Unit
}

//trait Reader1[+A[_]] {
//  // def tpe: Int
//  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): A[S]
//}

sealed trait MaybeSerializer[-Tx, -Acc, -A1, +A2] extends Writer[A1] with Reader[Tx, Acc, A2] {
  def isDefined: Boolean
}

case object NoSerializer extends MaybeSerializer[Any, Any, Any, Nothing] {
  // def instanceMethod: String = ...

  val isDefined = false

  def read(in: DataInput[Any, Any])(implicit tx: Any): Nothing = throw new NoSuchElementException

  def write(value: Any, out: DataOutput): Unit = throw new NoSuchElementException
}

object Serializer {
  implicit def option[Tx, Acc, A](implicit peer: Serializer[Tx, Acc, A]): Serializer[Tx, Acc, Option[A]] = ???

//  implicit object Writer extends de.sciss.lucre.stm3.Writer[Writer[Any]] {
//    def tpe: Int = 0x1234
//
//    def write(value: Writer[Any], out: DataOutput): Unit = {
//      out << ("A", value.tpe)
//      out.addSerializer(???)
//    }
//  }
}
trait Serializer[-Tx, -Acc, A] extends MaybeSerializer[Tx, Acc, A, A] {
  def isDefined = true
}

trait DataOutput {
  def <<   (name: String, value: Int): this.type
  def <<[A](name: String, value: A)(implicit writer: Writer[A]) : this.type

  def <<[A](name: String, tpe: Serializer[Any, Any, A])

  def addSerializer[A](serializer: Serializer[Any, Any, A]): Unit
}

trait DataInput[+Tx, +Acc] {
  def Int_<< (): Int
  // def << [A]()(implicit reader: Reader[A]): A

  // def Var_<< [Tx, Acc, A](name: String)(implicit reader: Reader[Tx, Acc, A]): A

  // def getSerializer[A](tpe: Int): Serializer[A]

  // def resolve[Tx, Acc, A](m: MaybeSerializer[Tx, Acc, A, A]): Serializer[Tx, Acc, A] = ???

  def << [Tx1 >: Tx, Acc1 >: Acc, A](name: String, m: MaybeSerializer[Tx, Acc, A, A]): Serializer[Tx1, Acc1, A] = ???

  def << [A](name: String)(implicit ser: Serializer[Tx, Acc, A]): A
}

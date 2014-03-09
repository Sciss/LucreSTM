package de.sciss.lucre.stm3

object List {
  def empty[S <: Sys[S], A](implicit tx: S#Tx): List[S, A] = {
    ??? // new Impl[S, A]
  }

  private final case class Impl[S <: Sys[S], A](headOption: S#Var[Option[Cell[S#Tx, A]]])
    extends List[S, A] {

    def prepend(value: A)(implicit tx: S#Tx): Unit = {
      val nextVar: S#Var[Option[Cell[S#Tx, A]]] = ???
      // val cell    = CellImpl(nextVar)(value)
      ???
    }

    def append (value: A)(implicit tx: S#Tx): Unit = ???
  }

  private final case class CellImpl[S <: Sys[S], A](nextOption: S#Var[Option[Cell[S#Tx, A]]])(value: A)

  object Cell {
    implicit def serializer[S <: Sys[S], A](implicit peer: Serializer[S#Tx, S#Acc, A]):
     Serializer[S#Tx, S#Acc, Cell[S#Tx, A]] = ???
  }
  trait Cell[-Tx, +A] {
    def value: A
    def nextOption: Source[Tx, Option[Cell[Tx, A]]]
  }

  //  implicit def serializer[S <: Sys[S], A]: Serializer[List[S, A]] = new Serializer[List[S, A]] {
  //    def tpe: Int = 0x2345
  //
  //    def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): List[S, A] = {
  //      ???
  //    }
  //
  //    def write(value: List[A], out: DataOutput): Unit = {
  //      ???
  //    }
  //  }

  implicit def serializer[S <: Sys[S], A](implicit peer: Serializer[S#Tx, S#Acc, A]):
    Serializer[S#Tx, S#Acc, List[S, A]] = new serializer[S, A](peer)

  private class serializer[S <: Sys[S], A](peerM: MaybeSerializer[S#Tx, S#Acc, A, A])
    extends Serializer[S#Tx, S#Acc, List[S, A]] {

    def tpe: Int = 0x2345

    def read(in: DataInput[S#Tx, S#Acc])(implicit tx: S#Tx): List[S, A] = {
      import in.<<
      implicit val peer = << ("A", peerM)
      // val headOption    = tx.readVar[Option[List.Cell[S#Tx, A]]]("head", in, access)
      val headOption  = << [Var[S#Tx, Option[List.Cell[S#Tx, A]]]]("head")
      ??? // new List.Impl[S, A](headOption)
    }

    def instanceMethod: String = s"${Impl.getClass.getName}#apply"

    def write(value: List[S, A], out: DataOutput): Unit = {

      ???
    }
  }
}
trait List[S <: Sys[S], A] {
  def headOption: Source[S#Tx, Option[List.Cell[S#Tx, A]]]
  def prepend(value: A)(implicit tx: S#Tx): Unit
  def append (value: A)(implicit tx: S#Tx): Unit
}

/*
 *  Serializer.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2013 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.lucre
package stm

import collection.immutable.{IndexedSeq => IIdxSeq}
import collection.mutable
import annotation.switch
import scala.{specialized => spec}
import stm.{SpecGroup => ialized}

object Serializer {
  implicit final val Unit    = ImmutableSerializer.Unit
  implicit final val Boolean = ImmutableSerializer.Boolean
  implicit final val Char    = ImmutableSerializer.Char
  implicit final val Int     = ImmutableSerializer.Int
  implicit final val Float   = ImmutableSerializer.Float
  implicit final val Long    = ImmutableSerializer.Long
  implicit final val Double  = ImmutableSerializer.Double
  implicit final val String  = ImmutableSerializer.String

  implicit def fromSerializer[A](implicit peer: ImmutableSerializer[A]): Serializer[Any, Any, A] = peer

  // ---- higher-kinded ----

  // Option is not specialized at the moment
  implicit def option[Tx, Acc, A](implicit peer: Serializer[Tx, Acc, A]): Serializer[Tx, Acc, Option[A]] =
    new OptionWrapper[Tx, Acc, A](peer)

  private final class OptionWrapper[Tx, Acc, A](peer: Serializer[Tx, Acc, A])
    extends Serializer[Tx, Acc, Option[A]] {

    def write(opt: Option[A], out: DataOutput) {
      opt match {
        case Some(v)  => out.writeUnsignedByte(1); peer.write(v, out)
        case _        => out.writeUnsignedByte(0)
      }
    }

    def read(in: DataInput, acc: Acc)(implicit tx: Tx): Option[A] = (in.readUnsignedByte(): @switch) match {
      case 1 => Some(peer.read(in, acc))
      case 0 => None
    }
  }

  // Either is not specialized at the moment
  implicit def either[Tx, Acc, A, B](implicit peer1: Serializer[Tx, Acc, A],
                                              peer2: Serializer[Tx, Acc, B]): Serializer[Tx, Acc, Either[A, B]] =
    new EitherWrapper[Tx, Acc, A, B](peer1, peer2)

  private final class EitherWrapper[Tx, Acc, A, B](peer1: Serializer[Tx, Acc, A], peer2: Serializer[Tx, Acc, B])
    extends Serializer[Tx, Acc, Either[A, B]] {

    def write(eith: Either[A, B], out: DataOutput) {
      eith match {
        case Left (a) => out.writeUnsignedByte(0); peer1.write(a, out)
        case Right(b) => out.writeUnsignedByte(1); peer2.write(b, out)
      }
    }

    def read(in: DataInput, acc: Acc)(implicit tx: Tx): Either[A, B] = (in.readUnsignedByte(): @switch) match {
      case 0 => Left (peer1.read(in, acc))
      case 1 => Right(peer2.read(in, acc))
    }
  }

  implicit def tuple2[Tx, Acc, @spec(ialized) A1, @spec(ialized) A2]
    (implicit peer1: Serializer[Tx, Acc, A1], peer2: Serializer[Tx, Acc, A2]): Serializer[ Tx, Acc, (A1, A2) ] =
    new Tuple2Wrapper[Tx, Acc, A1, A2]( peer1, peer2 )

  private final class Tuple2Wrapper[Tx, Acc, @spec(ialized) A1, @spec(ialized) A2]
    (peer1: Serializer[Tx, Acc, A1], peer2: Serializer[Tx, Acc, A2])
    extends Serializer[ Tx, Acc, (A1, A2) ] {

    def write(tup: (A1, A2), out: DataOutput) {
      peer1.write(tup._1, out)
      peer2.write(tup._2, out)
    }

    def read(in: DataInput, acc: Acc)(implicit tx: Tx): (A1, A2) = {
      val a1 = peer1.read(in, acc)
      val a2 = peer2.read(in, acc)
      (a1, a2)
    }
  }

  implicit def tuple3[Tx, Acc, A1, A2, A3](implicit peer1: Serializer[Tx, Acc, A1],
                                                    peer2: Serializer[Tx, Acc, A2],
                                                    peer3: Serializer[Tx, Acc, A3]): Serializer[Tx, Acc, (A1, A2, A3)] =
    new Tuple3Wrapper[Tx, Acc, A1, A2, A3](peer1, peer2, peer3)

  private final class Tuple3Wrapper[Tx, Acc, A1, A2, A3](peer1: Serializer[Tx, Acc, A1],
                                                         peer2: Serializer[Tx, Acc, A2],
                                                         peer3: Serializer[Tx, Acc, A3])
    extends Serializer[Tx, Acc, (A1, A2, A3)] {

    def write(tup: (A1, A2, A3), out: DataOutput) {
      peer1.write(tup._1, out)
      peer2.write(tup._2, out)
      peer3.write(tup._3, out)
    }

    def read(in: DataInput, acc: Acc)(implicit tx: Tx): (A1, A2, A3) = {
      val a1 = peer1.read(in, acc)
      val a2 = peer2.read(in, acc)
      val a3 = peer3.read(in, acc)
      (a1, a2, a3)
    }
  }

  implicit def list[Tx, Acc, A](implicit peer: Serializer[Tx, Acc, A]): Serializer[Tx, Acc, List[A]] =
    new ListSerializer[Tx, Acc, A](peer)

  implicit def set[Tx, Acc, A](implicit peer: Serializer[Tx, Acc, A]): Serializer[Tx, Acc, Set[A]] =
    new SetSerializer[Tx, Acc, A](peer)

  implicit def indexedSeq[Tx, Acc, A](implicit peer: Serializer[Tx, Acc, A]): Serializer[Tx, Acc, IIdxSeq[A]] =
    new IndexedSeqSerializer[Tx, Acc, A](peer)

  implicit def map[Tx, Acc, A, B](implicit peer: Serializer[Tx, Acc, (A, B)]): Serializer[Tx, Acc, Map[A, B]] =
    new MapSerializer[Tx, Acc, A, B](peer)

  // XXX size might be a slow operation on That...
  private sealed trait CollectionSerializer[Tx, Acc, A, That <: Traversable[A]] extends Serializer[Tx, Acc, That] {
    def newBuilder: mutable.Builder[A, That]

    def peer: Serializer[Tx, Acc, A]

    final def write(coll: That, out: DataOutput) {
      out.writeInt(coll.size)
      val ser = peer
      coll.foreach(ser.write(_, out))
    }

    final def read(in: DataInput, acc: Acc)(implicit tx: Tx): That = {
      var sz = in.readInt()
      val b = newBuilder
      val ser = peer
      while (sz > 0) {
        b += ser.read(in, acc)
        sz -= 1
      }
      b.result()
    }
  }

  private final class ListSerializer[Tx, Acc, A](val peer: Serializer[Tx, Acc, A])
    extends CollectionSerializer[Tx, Acc, A, List[A]] {
    def newBuilder = List.newBuilder[A]
  }

  private final class SetSerializer[Tx, Acc, A](val peer: Serializer[Tx, Acc, A])
    extends CollectionSerializer[Tx, Acc, A, Set[A]] {
    def newBuilder = Set.newBuilder[A]
  }

  private final class IndexedSeqSerializer[Tx, Acc, A](val peer: Serializer[Tx, Acc, A])
    extends CollectionSerializer[Tx, Acc, A, IIdxSeq[A]] {
    def newBuilder = IIdxSeq.newBuilder[A]
  }

  private final class MapSerializer[Tx, Acc, A, B](val peer: Serializer[Tx, Acc, (A, B)])
    extends CollectionSerializer[Tx, Acc, (A, B), Map[A, B]] {
    def newBuilder = Map.newBuilder[A, B]
  }
}

trait Serializer[-Tx, @spec(Unit) -Acc, @spec(ialized) A]
extends Reader[Tx, Acc, A ] with Writer[ A ]

trait MutableSerializer[S <: Sys[S], M <: Mutable[S#ID, S#Tx]]
  extends Serializer[S#Tx, S#Acc, M] {

  final def write(m: M, out: DataOutput) {
    m.write(out)
  }

  final def read(in: DataInput, access: S#Acc)(implicit tx: S#Tx): M = {
    val id = tx.readID(in, access)
    readData(in, id)
  }

  protected def readData(in: DataInput, id: S#ID)(implicit tx: S#Tx): M
}
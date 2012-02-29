/*
 *  LinkedList.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2012 Hanns Holger Rutz. All rights reserved.
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

import annotation.tailrec

object LinkedList {
   def empty[ S <: Sys[ S ], A ]( implicit tx: S#Tx, peerSer: TxnSerializer[ S#Tx, S#Acc, A ]) : LinkedList[ S, A ] =
      new ListNew[ S, A ]( tx )

   private sealed trait ListImpl[ S <: Sys[ S ], A ] extends LinkedList[ S, A ] {
      override def toString = "LinkedList" + id

      protected def headRef: S#Var[ Option[ Cell[ S, A ]]]
      protected def peerSer: TxnSerializer[ S#Tx, S#Acc, A ]

      final protected def disposeData()( implicit tx: S#Tx ) {
         headRef.dispose()
      }

      final protected def writeData( out: DataOutput ) {
         headRef.write( out )
      }

      final def prepend( elem: A )( implicit tx: S#Tx ) {
         val oldHead = headRef.get
         val c       = new Cell.New( tx, elem, oldHead, peerSer )
         headRef.set( Some( c ))
      }

      final def append( elem: A )( implicit tx: S#Tx ) {
         @tailrec def step( v: S#Var[ Option[ Cell[ S, A ]]]) { v.get match {
            case None =>
               v.set( Some( new Cell.New( tx, elem, None, peerSer )))
            case Some( cell ) =>
               step( cell.nextRef )
         }}
         step( headRef )
      }

      final def remove( elem: A )( implicit tx: S#Tx ) {
         @tailrec def step( v: S#Var[ Option[ Cell[ S, A ]]]) { v.get match {
            case None =>
            case Some( cell ) =>
               if( cell.value == elem ) {
                  v.set( cell.nextRef.get )
                  cell.dispose()

               } else step( cell.nextRef )
         }}
         step( headRef )
      }

      final def foreach( fun: A => Unit )( implicit tx: S#Tx ) {
         @tailrec def step( v: S#Var[ Option[ Cell[ S, A ]]]) { v.get match {
            case None =>
            case Some( cell ) =>
               fun( cell.value )
               step( cell.nextRef )
         }}
         step( headRef )
      }

      final def isEmpty( implicit tx: S#Tx ) : Boolean   = headRef.get.isEmpty
      final def nonEmpty( implicit tx: S#Tx ) : Boolean  = !isEmpty
   }

   private final class ListNew[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ]( tx0: S#Tx )(
      protected implicit val peerSer: TxnSerializer[ S#Tx, S#Acc, A ])
   extends ListImpl[ S, A ] {
      val id                  = tx0.newID()
      protected val headRef   = tx0.newVar[ Option[ Cell[ S, A ]]]( id, None )
   }

   private object Cell {
      implicit def serializer[ S <: Sys[ S ], A ](
         implicit peerSer: TxnSerializer[ S#Tx, S#Acc, A ]) : TxnSerializer[ S#Tx, S#Acc, Cell[ S, A ]] =
            new Ser[ S, A ]( peerSer )

      private final class Ser[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ](
         peerSer: TxnSerializer[ S#Tx, S#Acc, A ])
      extends TxnSerializer[ S#Tx, S#Acc, Cell[ S, A ]] {
         def write( v: Cell[ S, A ], out: DataOutput ) {
            v.write( out )
         }

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Cell[ S, A ] =
            new Read[ S, A ]( tx, in, access, peerSer )
      }

      final class New[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ](
         tx0: S#Tx, val value: A, initNext: Option[ Cell[ S, A ]],
         protected implicit val peerSer: TxnSerializer[ S#Tx, S#Acc, A ])
      extends Cell[ S, A ] {
         val id      = tx0.newID()
         val nextRef = tx0.newVar[ Option[ Cell[ S, A ]]]( id, initNext )
      }

      private final class Read[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ](
         tx0: S#Tx, in: DataInput, access: S#Acc, protected implicit val peerSer: TxnSerializer[ S#Tx, S#Acc, A ])
      extends Cell[ S, A ] {
         val id      = tx0.readID( in, access )
         val nextRef = tx0.readVar[ Option[ Cell[ S, A ]]]( id, in )
         val value   = peerSer.read( in, access )( tx0 )
      }
   }
   private sealed trait Cell[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ] extends Mutable[ S ] {
      protected def peerSer: TxnSerializer[ S#Tx, S#Acc, A ]

      def value: A
      def nextRef: S#Var[ Option[ Cell[ S, A ]]]

      final protected def disposeData()( implicit tx: S#Tx ) { nextRef.dispose() }
      final protected def writeData( out: DataOutput ) {
         nextRef.write( out )
         peerSer.write( value, out )
      }
   }
}

trait LinkedList[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ] extends Mutable[ S ] {
   def prepend( elem: A )( implicit tx: S#Tx ) : Unit
   def append( elem: A )( implicit tx: S#Tx ) : Unit
   def remove( elem: A )( implicit tx: S#Tx ) : Unit
   def foreach( fun: A => Unit )( implicit tx: S#Tx ) : Unit
   def isEmpty( implicit tx: S#Tx ) : Boolean
   def nonEmpty( implicit tx: S#Tx ) : Boolean

//   def map( ) : Unit
//   def flatMap( ) : Unit
}
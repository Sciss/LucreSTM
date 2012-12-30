/*
 *  LinkedList.scala
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

/*
package de.sciss.lucre
package stm

import annotation.tailrec

object LinkedList {
   def empty[ S <: Sys[ S ], A ]( implicit tx: S#Tx, elemSerializer: Serializer[ S#Tx, S#Acc, A ]) : LinkedList[ S, A ] = {
      val id      = tx.newID()
      val headRef = tx.newVar[ Option[ Cell[ S, A ]]]( id, None )
      new Impl[ S, A ]( id, headRef )
   }

   def read[ S <: Sys[ S ], A ]( in: DataInput, access: S#Acc )
                               ( implicit tx: S#Tx, elemSerializer: Serializer[ S#Tx, S#Acc, A ]) : LinkedList[ S, A ] = {
      val id      = tx.readID( in, access )
      val headRef = tx.readVar[ Option[ Cell[ S, A ]]]( id, in )
      new Impl[ S, A ]( id, headRef )
   }

   private final class Impl[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ](
      val id: S#ID, headRef: S#Var[ Option[ Cell[ S, A ]]])( implicit elemSerializer: Serializer[ S#Tx, S#Acc, A ])
   extends LinkedList[ S, A ] with Mutable.Impl[ S ] {
      override def toString = "LinkedList" + id

//      protected def headRef: S#Var[ Option[ Cell[ S, A ]]]
//      protected def peerSer: Serializer[ S#Tx, S#Acc, A ]

      protected def disposeData()( implicit tx: S#Tx ) {
         headRef.dispose()
      }

      protected def writeData( out: DataOutput ) {
         headRef.write( out )
      }

      def prepend( elem: A )( implicit tx: S#Tx ) {
         val cellID  = tx.newID()
         val nextRef = tx.newVar( cellID, headRef.get )
         val c       = new Cell( cellID, nextRef, elem )
         headRef.set( Some( c ))
      }

      def append( elem: A )( implicit tx: S#Tx ) {
         val cellID  = tx.newID()
         val nextRef = tx.newVar( cellID, Option.empty[ Cell[ S, A ]])
         val c       = new Cell( cellID, nextRef, elem )

         @tailrec def step( v: S#Var[ Option[ Cell[ S, A ]]]) { v.get match {
            case None =>
               v.set( Some( c ))
            case Some( cell ) =>
               step( cell.nextRef )
         }}
         step( headRef )
      }

      def remove( elem: A )( implicit tx: S#Tx ) {
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

      def foreach( fun: A => Unit )( implicit tx: S#Tx ) {
         @tailrec def step( v: S#Var[ Option[ Cell[ S, A ]]]) { v.get match {
            case None =>
            case Some( cell ) =>
               fun( cell.value )
               step( cell.nextRef )
         }}
         step( headRef )
      }

      def isEmpty( implicit tx: S#Tx ) : Boolean   = headRef.get.isEmpty
      def nonEmpty( implicit tx: S#Tx ) : Boolean  = !isEmpty
   }

   private implicit def cellSer[ S <: Sys[ S ], A ]( implicit elemSerializer: Serializer[ S#Tx, S#Acc, A ]) : Serializer[ S#Tx, S#Acc, Cell[ S, A ]] =
      new CellSer

   private final class CellSer[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ](
      implicit elemSerializer: Serializer[ S#Tx, S#Acc, A ])
   extends Serializer[ S#Tx, S#Acc, Cell[ S, A ]] {

      implicit def self = this

      def write( v: Cell[ S, A ], out: DataOutput ) {
         v.write( out )
      }

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Cell[ S, A ] = {
         val id      = tx.readID( in, access )
         val nextRef = tx.readVar[ Option[ Cell[ S, A ]]]( id, in )
         val value   = elemSerializer.read( in, access )
         new Cell( id, nextRef, value )
      }
   }

//         val id      = tx0.newID()
//         val nextRef = tx0.newVar[ Option[ Cell[ S, A ]]]( id, initNext )

   private final class Cell[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ](
      val id: S#ID, val nextRef: S#Var[ Option[ Cell[ S, A ]]], val value: A )(
      implicit elemSerializer: Serializer[ S#Tx, S#Acc, A ])
   extends Mutable.Impl[ S ] {
      protected def disposeData()( implicit tx: S#Tx ) { nextRef.dispose() }
      protected def writeData( out: DataOutput ) {
         nextRef.write( out )
         elemSerializer.write( value, out )
      }
   }
}

/**
 * A transactional and mutable single linked list.
 *
 * The following operations are O(1): `prepend`, `isEmpty`, `nonEmpty`
 * The following operations are O(n): `append`, `remove`
 *
 * @tparam A   the element type stored in the list
 */
trait LinkedList[ S <: Sys[ S ], @specialized( Int, Float, Long, Double, Boolean ) A ] extends Mutable[ S#ID, S#Tx ] {
   def prepend( elem: A )( implicit tx: S#Tx ) : Unit
   def append( elem: A )( implicit tx: S#Tx ) : Unit
   def remove( elem: A )( implicit tx: S#Tx ) : Unit
   def foreach( fun: A => Unit )( implicit tx: S#Tx ) : Unit
   def isEmpty( implicit tx: S#Tx ) : Boolean
   def nonEmpty( implicit tx: S#Tx ) : Boolean

   // def iterator: Iterator[ S#Tx, S#Acc, A ]

//   def map( ) : Unit
//   def flatMap( ) : Unit
}
*/
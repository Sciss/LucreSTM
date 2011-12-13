/*
 *  InMemory.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref, Val => _Val, Txn => _Txn}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}

object InMemory {
//   sealed trait Mut[ +A ] extends Mutable[ InTxn, A ]
   sealed trait Val[ @specialized A ] extends _Val[ Txn, A ]
   sealed trait Ref[ A ] extends _Ref[ Txn, /* Mut, */ A ]

   private sealed trait SourceImpl[ @specialized A ] {
      protected def peer: ScalaRef[ A ]
      def set( v: A )( implicit tx: Txn ) { peer.set( v )( tx.peer )}
      def get( implicit tx: Txn ) : A = peer.get( tx.peer )
      def transform( f: A => A )( implicit tx: Txn ) { peer.transform( f )( tx.peer )}
      def dispose()( implicit tx: Txn ) { peer.set( null.asInstanceOf[ A ])( tx.peer )}
      def write( out: DataOutput ) {}

//      def debug() {}
//      def update( v: A )( implicit tx: InTxn ) { peer.set( v )}
//      def apply()( implicit tx: InTxn ) : A = peer.get
   }

   private final class RefImpl[ A ]( protected val peer: ScalaRef[ A ])
   extends Ref[ A ] with SourceImpl[ A ] {
//      def getOrNull( implicit tx: InTxn ) : A = get.orNull

      override def toString = "Ref<" + hashCode().toHexString + ">"
   }

   private final class ValImpl[ @specialized A ]( protected val peer: ScalaRef[ A ])
   extends Val[ A ] with SourceImpl[ A ] {
      override def toString = "Val<" + hashCode().toHexString + ">"
   }

   private def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   sealed trait ID extends Identifier[ Txn ]

   private final class IDImpl extends ID {
      def write( out: DataOutput ) {}
      def dispose()( implicit tx: Txn ) {}
      override def toString = "<" + hashCode().toHexString + ">"
   }

   sealed trait Txn extends _Txn[ InMemory ]

   private final class TxnImpl( val system: InMemory, val peer: InTxn ) extends Txn {
      private[lucrestm] def newVal[ A ]( id: ID, init: A )( implicit ser: Serializer[ A ]) : Val[ A ] = {
         val peer = ScalaRef[ A ]( init )
         new InMemory.ValImpl[ A ]( peer )
      }

      private[lucrestm] def newInt( id: ID, init: Int ) : Val[ Int ] = {
         val peer = ScalaRef( init )
         new InMemory.ValImpl( peer )
      }

      private[lucrestm] def newRef[ A <: Mutable[ InMemory ]]( id: ID, init: A )(
         implicit reader: MutableReader[ InMemory, A ]) : Ref[ A ] = {

         val peer = ScalaRef[ A ]( init )
         new InMemory.RefImpl[ A ]( peer )
      }

      private[lucrestm] def newOptionRef[ A <: MutableOption[ InMemory ]]( id: ID, init: A )(
         implicit reader: MutableOptionReader[ InMemory, A ]) : Ref[ A ] = {

         val peer = ScalaRef[ A ]( init )
         new InMemory.RefImpl[ A ]( peer )
      }

      private[lucrestm] def newValArray[ A ]( size: Int ) = new Array[ Val[ A ]]( size )
      private[lucrestm] def newRefArray[ A ]( size: Int ) = new Array[ Ref[ A ]]( size )
   }
}

/**
 * A thin wrapper around scala-stm.
 */
final class InMemory extends Sys[ InMemory ] {
   type Val[ @specialized A ]  = InMemory.Val[ A ]
   type Ref[ A ]  = InMemory.Ref[ A ]
   type ID        = InMemory.ID
   type Tx        = InMemory.Txn

   def manifest: Manifest[ InMemory ] = Manifest.classType( classOf[ InMemory ])

   def newID()( implicit tx: Tx ) : ID = new InMemory.IDImpl

   def atomic[ Z ]( block: Tx => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( itx => block( new InMemory.TxnImpl( this, itx )))
   }

   def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Val[ A ] = {
      InMemory.opNotSupported( "readVal" )
   }

   def readInt( in: DataInput ) : Val[ Int ] = {
      InMemory.opNotSupported( "readIntVal" )
   }

   def readRef[ A <: Mutable[ InMemory ]]( in: DataInput )
                                         ( implicit reader: MutableReader[ InMemory, A ]) : Ref[ A ] = {
      InMemory.opNotSupported( "readRef" )
   }

   def readOptionRef[ A <: MutableOption[ InMemory ]]( in: DataInput )
                                                     ( implicit reader: MutableOptionReader[ InMemory, A ]) : Ref[ A ] = {
      InMemory.opNotSupported( "readOptionRef" )
   }

   def readMut[ A <: Mutable[ InMemory ]]( in: DataInput )( implicit reader: MutableReader[ InMemory, A ]) : A = {
      InMemory.opNotSupported( "readMut" )
   }

   def readOptionMut[ A <: MutableOption[ InMemory ]]( in: DataInput )
                                                     ( implicit reader: MutableOptionReader[ InMemory, A ]) : A = {
      InMemory.opNotSupported( "readOptionMut" )
   }
}
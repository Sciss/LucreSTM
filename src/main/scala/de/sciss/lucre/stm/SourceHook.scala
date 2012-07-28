/*
 *  SourceHook.scala
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

package de.sciss.lucre.stm

import de.sciss.lucre.{DataOutput, DataInput}

object SourceHook {
//   implicit def serializer[ S <: Sys[ S ], A ]( implicit peerSerializer: TxnSerializer[ S#Tx, S#Acc, A ]) : TxnSerializer[ S#Tx, S#Acc, SourceHook[ S#Tx, A ]] =
//      new Ser[ S, A ]

   def serializer[ S <: Sys[ S ], A <: Writer ](
      peer: (=> Source[ S#Tx, A ]) => TxnSerializer[ S#Tx, S#Acc, A ]) : TxnSerializer[ S#Tx, S#Acc, SourceHook[ S#Tx, A ]] =
      new Ser[ S, A ]( peer )

   private final class Ser[ S <: Sys[ S ], A <: Writer ]( peer: (=> Source[ S#Tx, A ]) => TxnSerializer[ S#Tx, S#Acc, A ])
   extends TxnSerializer[ S#Tx, S#Acc, SourceHook[ S#Tx, A ]] {
      def write( hook: SourceHook[ S#Tx, A ], out: DataOutput ) { hook.write( out )}
      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : SourceHook[ S#Tx, A ] = {
         new Impl[ S, A ] {
//            def peerSerializer: Source[ S#Tx, A ] => TxnSerializer[ S#Tx, S#Acc, A ] = peer
            val id               = tx.readID( in, access )
            val v: S#Var[ A ]    = tx.readVar[ A ]( id, in )( this )
            def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : A = {
               peer( source ).read( in, access )
            }
         }
      }
   }

   private abstract class Impl[ S <: Sys[ S ], A <: Writer ] extends SourceHook[ S#Tx, A ] with TxnSerializer[ S#Tx, S#Acc, A ] {
      protected def id: S#ID
      protected def v: S#Var[ A ]
//      protected def peerSerializer: Source[ S#Tx, A ] => TxnSerializer[ S#Tx, S#Acc, A ]

      final def source: Source[ S#Tx, A ] = v
      final def write( out: DataOutput ) {
         id.write( out )
         v.write( out )
      }

      final def dispose()( implicit tx: S#Tx ) {
         id.dispose()
         v.dispose()
      }

      // ---- TxnSerializer[ S#Tx, S#Acc, A ] ----
      def write( peer: A, out: DataOutput ) {
         peer.write( out )
      }

//      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : A = {
//         peerSerializer( source ).read( in, access )
//      }
   }
}

/**
 * An object which wraps a `Source`. A `SourceHook` can be used to create a storable
 * access to a transactional object, because instead of serializing the object directly,
 * its readable access is serialized instead. Therefore, if a `SourceHook` is recovered,
 * the transactional object can be "refreshed" by calling `source.get`.
 *
 * @tparam Tx  the transaction type of the source
 * @tparam A   the payload type of the source
 */
sealed trait SourceHook[ -Tx, +A ] extends Writer with Disposable[ Tx ] {
   def source: Source[ Tx, A ]
}
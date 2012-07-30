/*
 *  IdentifierMapImpl.scala
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
package impl

import concurrent.stm.TMap
import stm.{ Txn => _Txn }

object IdentifierMapImpl {
   def newInMemoryIntMap[ Txn <: _Txn[ _ ], ID, A ]( implicit intView: ID => Int )
      : IdentifierMap[ Txn, ID, A ] with Writer with Disposable[ Txn ] = new InMemoryInt[ Txn, ID, A ]( intView )

   private final class InMemoryInt[ Txn <: _Txn[ _ ], ID, A ]( intView: ID => Int )
   extends IdentifierMap[ Txn, ID, A ] with Writer with Disposable[ Txn ] {
      private val peer = TMap.empty[ Int, A ]

      def get( id: ID )( implicit tx: Txn ) : Option[ A ] = peer.get( intView( id ))( tx.peer )
      def getOrElse( id: ID, default: => A )( implicit tx: Txn ) : A = get( id ).getOrElse( default )
      def put( id: ID, value: A )( implicit tx: Txn ) { peer.put( intView( id ), value )( tx.peer )}
      def contains( id: ID )( implicit tx: Txn ) : Boolean = peer.contains( intView( id ))( tx.peer )
      def remove( id: ID )( implicit tx: Txn ) { peer.remove( intView( id ))( tx.peer )}

      override def toString = "IdentifierMap"

      def write( out: DataOutput ) {}
      def dispose()( implicit tx: Txn ) {}
   }
}

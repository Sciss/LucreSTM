/*
 *  Sys.scala
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

import stm.{Var => _Var}

object Sys {
// this produces 'diverging ...' messages
//   implicit def fromTxn[ S <: Sys[ S ]]( implicit tx: S#Tx ) : S = tx.system
   implicit def manifest[ S <: Sys[ S ]]( implicit system: S ) : Manifest[ S ] = system.manifest
}

trait Sys[ S <: Sys[ S ]] {
   type Var[ @specialized A ] <: _Var[ S#Tx, A ]
   type Tx <: Txn[ S ]
   type ID <: Identifier[ S#Tx ]
   type Acc
   type Entry[ A ] <: Var[ A ]

   def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ]

   def manifest: Manifest[ S ]

   /**
    * Reads the root object representing the stored data structure,
    * or provides a newly initialized one via the `init` argument,
    * if no root has been stored yet.
    */
   def root[ A ]( init: S#Tx => A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ]

   /**
    * Closes the underlying database (if the system is durable). The STM cannot be used beyond this call.
    */
   def close() : Unit
}
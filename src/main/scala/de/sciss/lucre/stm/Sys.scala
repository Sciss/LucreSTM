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
// this produces 'diverging fuckyourself' messages
//   implicit def fromTxn[ S <: Sys[ S ]]( implicit tx: S#Tx ) : S = tx.system
   implicit def manifest[ S <: Sys[ S ]]( implicit system: S ) : Manifest[ S ] = system.manifest
}

trait Sys[ S <: Sys[ S ]] {
   type Var[ @specialized A ] <: _Var[ S#Tx, A ]
   type Tx <: Txn[ S ]
   type ID <: Identifier[ S#Tx ]
   type Acc

//   final type ObsVar[ A ] = S#Var[ A ] with State[ S, Change[ A ]]

   // should get rid of this in Sys, too
   def atomic[ A ]( fun: S#Tx => A ) : A
//   def atomicRead[ A, B ]( read: TxnReader[ S#Tx, S#Acc, A ])( fun: (S#Tx, A) => B ) : B

//   def atomicAccess[ A ]( fun: (S#Tx, S#Acc) => A ) : A

//   def atomicAccess[ A, B ]( source: S#Var[ A ])( fun: (S#Tx, A) => B ) : B

   def manifest: Manifest[ S ]
}
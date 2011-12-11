/*
 *  Sys.scala
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

import de.sciss.lucrestm.{Ref => _Ref, Val => _Val}

object Sys {
// this produces 'diverging fuckyourself' messages
//   implicit def fromTxn[ S <: Sys[ S ]]( implicit tx: S#Tx ) : S = tx.system
   implicit def manifest[ S <: Sys[ S ]]( implicit system: S ) : Manifest[ S ] = system.manifest
}
trait Sys[ S <: Sys[ S ]] {
   type Val[ @specialized A ] <: _Val[ S#Tx, A ]
   type Ref[ A ] <: _Ref[ S#Tx, A ]
//   type Tx <: InTxn
   type Tx <: Txn[ S ]
   type ID <: Identifier[ S#Tx ]

   def newVal[ A ]( init: A )( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Val[ A ]
   def newInt( init: Int )( implicit tx: S#Tx ) : S#Val[ Int ]
   def newRef[ A <: Mutable[ S ]]( init: A )( implicit tx: S#Tx, reader: MutableReader[ S, A ]) : S#Ref[ A ]
   def newOptionRef[ A <: MutableOption[ S ]]( init: A )( implicit tx: S#Tx, reader: MutableOptionReader[ S, A ]) : S#Ref[ A ]
   def newID()( implicit tx: S#Tx ) : ID

   def atomic[ Z ]( block: S#Tx => Z ) : Z

   def newValArray[ A ]( size: Int ) : Array[ S#Val[ A ]]
   def newRefArray[ A /* <: Mutable[ S ]*/]( size: Int ) : Array[ S#Ref[ A ]]

   def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : S#Val[ A ]
   def readInt( in: DataInput ) : S#Val[ Int ]
   def readRef[ A <: Mutable[ S ]]( in: DataInput )( implicit reader: MutableReader[ S, A ]) : S#Ref[ A ]
   def readOptionRef[ A <: MutableOption[ S ]]( in: DataInput )( implicit reader: MutableOptionReader[ S, A ]) : S#Ref[ A ]
   def readMut[ A <: Mutable[ S ]]( in: DataInput )( implicit reader: MutableReader[ S, A ]) : A
   def readOptionMut[ A <: MutableOption[ S ]]( in: DataInput )( implicit reader: MutableOptionReader[ S, A ]) : A

   def manifest: Manifest[ S ]
}
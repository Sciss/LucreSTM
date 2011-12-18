/*
 *  Var.scala
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

sealed trait Sink[ -Tx, @specialized -A ] {
   def set( v: A )( implicit tx: Tx ) : Unit
}

sealed trait Source[ -Tx, @specialized +A ] extends Writer with Disposable[ Tx ] {
   def get( implicit tx: Tx ) : A
}

sealed trait RefLike[ -Tx, A ] extends Sink[ Tx, A ] with Source[ Tx, A ]

trait Var[ -Tx, @specialized A ] extends RefLike[ Tx, A ] {
   def transform( f: A => A )( implicit tx: Tx ) : Unit
}

//trait Ref[ -Tx, A ] extends RefLike[ Tx, A ]

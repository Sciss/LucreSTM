/*
 *  Selector.scala
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
package event

import stm.{TxnSerializer, Sys}
import scala.util.MurmurHash

object Selector {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] = new Ser[ S ]

   def apply[ S <: Sys[ S ]]( slot: Int, targets: Targets[ S ], invariant: Boolean ) : ReactorSelector[ S ] = {
      if( invariant ) InvariantTargetsSelector( slot, targets )
      else            MutatingTargetsSelector(  slot, targets )
   }

   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] {
      def write( v: Selector[ S ], out: DataOutput ) {
         v.writeSelector( out )
      }

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Selector[ S ] = {
         val cookie = in.readUnsignedByte()
         // 0 = invariant, 1 = mutating, 2 = observer
         if( cookie == 0 || cookie == 1 ) {
            val slot    = in.readInt()
            val reactor = Targets.readAndExpand[ S ]( in, access )
            reactor.select( slot, cookie == 0 )
         } else if( cookie == 2 ) {
            val id = in.readInt()
            new ObserverKey[ S ]( id )
         } else {
            sys.error( "Unexpected cookie " + cookie )
         }
      }
   }

   private sealed trait TargetsSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
      final private[event] def nodeSelectorOption: Option[ NodeSelector[ S, _ ]] = None
   }

   private final case class InvariantTargetsSelector[ S <: Sys[ S ]]( slot: Int, reactor: Targets[ S ])
   extends TargetsSelector[ S ] with InvariantSelector[ S ]

   private final case class MutatingTargetsSelector[ S <: Sys[ S ]]( slot: Int, reactor: Targets[ S ])
   extends TargetsSelector[ S ] with MutatingSelector[ S ]
}

sealed trait Selector[ S <: Sys[ S ]] /* extends Writer */ {
   protected def cookie: Int

   final def writeSelector( out: DataOutput ) {
      out.writeUnsignedByte( cookie )
      writeSelectorData( out )
   }

   protected def writeSelectorData( out: DataOutput ) : Unit

   private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) : Unit // ( implicit tx: S#Tx ) : Unit
   private[event] def toObserverKey : Option[ ObserverKey[ S ]] // Option[ Int ]
}

sealed trait ReactorSelector[ S <: Sys[ S ]] extends Selector[ S ] {
   private[event] def reactor: Reactor[ S ]
   private[event] def slot: Int

   private[event] def nodeSelectorOption: Option[ NodeSelector[ S, _ ]]

   final protected def writeSelectorData( out: DataOutput ) {
      out.writeInt( slot )
      reactor.id.write( out )
   }

   override def hashCode : Int = {
      import MurmurHash._
      var h = startHash( 2 )
      val c = startMagicA
      val k = startMagicB
      h = extendHash( h, slot, c, k )
      h = extendHash( h, reactor.id.##, nextMagicA( c ), nextMagicB( k ))
      finalizeHash( h )
   }

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ ReactorSelector[ _ ]]) {
         val thatSel = that.asInstanceOf[ ReactorSelector[ _ ]]
         (slot == thatSel.slot && reactor.id == thatSel.reactor.id)
      } else super.equals( that ))
   }

   final private[event] def toObserverKey : Option[ ObserverKey[ S ]] = None

   override def toString = reactor.toString + ".select(" + slot + ")"
}

sealed trait ExpandedSelector[ S <: Sys[ S ]] extends Selector[ S ] /* with Writer */ {
   private[event] def writeValue()( implicit tx: S#Tx ) : Unit
}

/* sealed */ trait NodeSelector[ S <: Sys[ S ], +A ] extends ReactorSelector[ S ] with ExpandedSelector[ S ] {
   private[event] def reactor: Node[ S, _ ]

   final private[event] def nodeSelectorOption: Option[ NodeSelector[ S, _ ]] = Some( this )

   private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]

   final private[event] def writeValue()( implicit tx: S#Tx ) {
      tx.writeVal[ Reactor[ S ]]( reactor.id, reactor )( new Targets.ExpanderSerializer[ S ])
   }
}

trait InvariantSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
   final protected def cookie: Int = 0

   final private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) {
      push.visit( this, parent )
   }
}

trait MutatingSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
   final protected def cookie: Int = 1

//   final private[event] def _invalidate()( implicit tx: S#Tx ) {
//      reactor._targets.invalidate( slot )
//   }

//   final /* protected */ def invalidate()( implicit tx: S#Tx ) {
////      _invalidate()
//      reactor._targets.invalidate( slot )
//   }
//   final /* protected */ def isInvalid( implicit tx: S#Tx ) : Boolean = reactor._targets.isInvalid( slot )
//   final /* protected */ def validated()( implicit tx: S#Tx ) { reactor._targets.validated( slot )}

   final private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) {
      push.markInvalid( this )
      push.visit( this, parent )
   }
}

/**
 * Instances of `ObserverKey` are provided by methods in `Txn`, when a live `Observer` is registered. Since
 * the observing function is not persisted, the slot will be used for lookup (again through the transaction)
 * of the reacting function during the first reaction gathering phase of event propagation.
 */
final case class ObserverKey[ S <: Sys[ S ]] private[lucre] ( id: Int ) extends ExpandedSelector[ S ] {
   protected def cookie: Int = 2

   private[event] def toObserverKey : Option[ ObserverKey[ S ]] = Some( this )

   private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) { push.addLeaf( this, parent )}

   private[event] def writeValue()( implicit tx: S#Tx ) {}  // we are light weight, nothing to do here

   def dispose()( implicit tx: S#Tx ) {}  // XXX really?

   protected def writeSelectorData( out: DataOutput ) {
      out.writeInt( id )
   }
}

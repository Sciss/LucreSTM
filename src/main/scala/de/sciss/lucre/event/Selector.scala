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

   private[event] def apply[ S <: Sys[ S ]]( slot: Int, node: VirtualNode.Raw[ S ],
                                             invariant: Boolean ) : VirtualNodeSelector[ S ] = {
      if( invariant ) InvariantTargetsSelector( slot, node )
      else            MutatingTargetsSelector(  slot, node )
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
// MMM
//            val reactor = Targets.readAndExpand[ S ]( in, access )
val fullSize = in.readInt()
val reactor  = VirtualNode.read[ S ]( in, fullSize, access )
            reactor.select( slot, cookie == 0 )
         } else if( cookie == 2 ) {
            val id = in.readInt()
            new ObserverKey[ S ]( id )
         } else {
            sys.error( "Unexpected cookie " + cookie )
         }
      }
   }

   private sealed trait TargetsSelector[ S <: Sys[ S ]] extends VirtualNodeSelector[ S ] {
//      override protected def reactor: Targets[ S ]
//      override private[event] def reactor: Targets[ S ]
//      protected def data: Array[ Byte ]
//      protected def access: S#Acc
      override private[event] def node: VirtualNode.Raw[ S ]

//      final private[event] def nodeSelectorOption: Option[ NodeSelector[ S, _ ]] = None

//      final protected def writeVirtualNode( out: DataOutput ) {
//         reactor.write( out )
//         out.write( data )
//      }

      final private[event] def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : NodeSelector[ S, _ ] = {
         node.devirtualize( reader ).select( slot, cookie == 0 )
      }
   }

   private final case class InvariantTargetsSelector[ S <: Sys[ S ]]( slot: Int, node: VirtualNode.Raw[ S ])
   extends TargetsSelector[ S ] with InvariantSelector[ S ]

   private final case class MutatingTargetsSelector[ S <: Sys[ S ]]( slot: Int, node: VirtualNode.Raw[ S ])
   extends TargetsSelector[ S ] with MutatingSelector[ S ]
}

sealed trait Selector[ S <: Sys[ S ]] /* extends Writer */ {
   protected def cookie: Int

   final def writeSelector( out: DataOutput ) {
      out.writeUnsignedByte( cookie )
      writeSelectorData( out )
   }

   protected def writeSelectorData( out: DataOutput ) : Unit

   private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) : Unit // ( implicit tx: S#Tx ) : Unit
   private[event] def toObserverKey : Option[ ObserverKey[ S ]] // Option[ Int ]
}

sealed trait VirtualNodeSelector[ S <: Sys[ S ]] extends Selector[ S ] {
//   private[event] def reactor: Reactor[ S ]
   private[event] def node: VirtualNode[ S ]
   private[event] def slot: Int

//   private[event] def nodeSelectorOption: Option[ NodeSelector[ S, _ ]]
   final protected def writeSelectorData( out: DataOutput ) {
      out.writeInt( slot )
      val sizeOffset = out.getBufferLength
      out.writeInt( 0 ) // will be overwritten later -- note: addSize cannot be used, because the subsequent write will be invalid!!!
      node.write( out )
      val stop       = out.getBufferLength
      val delta      = stop - sizeOffset
      out.addSize( -delta )      // XXX ugly ... should have a seek method
      val fullSize   = delta - 4
      out.writeInt( fullSize )
      out.addSize( fullSize )
   }

   private[event] def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : NodeSelector[ S, _ ]

// MMM
//   final protected def writeSelectorData( out: DataOutput ) {
//      out.writeInt( slot )
//      reactor.id.write( out )
//   }

   override def hashCode : Int = {
      import MurmurHash._
      var h = startHash( 2 )
      val c = startMagicA
      val k = startMagicB
      h = extendHash( h, slot, c, k )
      h = extendHash( h, node.id.##, nextMagicA( c ), nextMagicB( k ))
      finalizeHash( h )
   }

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ VirtualNodeSelector[ _ ]]) {
         val thatSel = that.asInstanceOf[ VirtualNodeSelector[ _ ]]
         (slot == thatSel.slot && node.id == thatSel.node.id)
      } else super.equals( that ))
   }

   final private[event] def toObserverKey : Option[ ObserverKey[ S ]] = None

   override def toString = node.toString + ".select(" + slot + ")"
}

// MMM
//sealed trait ExpandedSelector[ S <: Sys[ S ]] extends Selector[ S ] /* with Writer */ {
//// MMM
////   private[event] def writeValue()( implicit tx: S#Tx ) : Unit
//}

/* sealed */ trait NodeSelector[ S <: Sys[ S ], +A ] extends VirtualNodeSelector[ S ] /* MMM with ExpandedSelector[ S ] */ {
   private[event] def node: Node[ S ]

//   final private[event] def nodeSelectorOption: Option[ NodeSelector[ S, _ ]] = Some( this )
//   final protected def writeSelectorData( out: DataOutput ) {
//      out.writeInt( slot )
//      val sizeOffset = out.getBufferOffset
//      out.addSize( 4 )
//      reactor.write( out )
//      val pos        = out.getBufferOffset
//      val delta      = pos - sizeOffset
//      out.addSize( -delta )
//   }

   final private[event] def devirtualize( reader: Reader[ S, Node[ S ]])( implicit tx: S#Tx ) : NodeSelector[ S, _ ] =
      this

   private[lucre] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ A ]

// MMM
//   final private[event] def writeValue()( implicit tx: S#Tx ) {
//      tx.writeVal[ VirtualNode[ S ]]( reactor.id, reactor ) // ( new Targets.ExpanderSerializer[ S ])
//   }
}

trait InvariantSelector[ S <: Sys[ S ]] extends VirtualNodeSelector[ S ] {
   final protected def cookie: Int = 0

   final private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) {
      push.visit( this, parent )
   }
}

trait MutatingSelector[ S <: Sys[ S ]] extends VirtualNodeSelector[ S ] {
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

   final private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) {
      push.markInvalid( this )
      push.visit( this, parent )
   }
}

/**
 * Instances of `ObserverKey` are provided by methods in `Txn`, when a live `Observer` is registered. Since
 * the observing function is not persisted, the slot will be used for lookup (again through the transaction)
 * of the reacting function during the first reaction gathering phase of event propagation.
 */
final case class ObserverKey[ S <: Sys[ S ]] private[lucre] ( id: Int ) extends /* MMM Expanded */ Selector[ S ] {
   protected def cookie: Int = 2

   private[event] def toObserverKey : Option[ ObserverKey[ S ]] = Some( this )

   private[event] def pushUpdate( parent: VirtualNodeSelector[ S ], push: Push[ S ]) {
//      val reader  = push
//      val nParent = parent.devirtualize( reader )
////
////      val nParent = parent.nodeSelectorOption.getOrElse(
////         sys.error( "Orphan observer " + this + " - no expanded node selector" )
////      )
      push.addLeaf( this, parent )
   }

// MMM
//   private[event] def writeValue()( implicit tx: S#Tx ) {}  // we are light weight, nothing to do here

   def dispose()( implicit tx: S#Tx ) {}  // XXX really?

   protected def writeSelectorData( out: DataOutput ) {
      out.writeInt( id )
   }
}

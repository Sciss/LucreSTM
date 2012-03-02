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

import stm.{TxnSerializer, Writer, Sys}
import annotation.switch
import scala.util.MurmurHash

object Selector {
   implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] = new Ser[ S ]

   implicit def event[ S <: Sys[ S ]]( ev: Event[ S, _, _ ])( implicit tx: S#Tx ) : ReactorSelector[ S ] with ExpandedSelector[ S ] = ev.select()

   def apply[ S <: Sys[ S ]]( key: Int, targets: Invariant.Targets[ S ]) : ReactorSelector[ S ] =
      new InvariantTargetsSelector[ S ]( key, targets )

   def apply[ S <: Sys[ S ] /*, A */]( key: Int, node: Invariant[ S, _ /* A */]) : NodeSelector[ S ] = {
      new InvariantNodeSelector[ S /*, A, Invariant[ S, A ] */]( key, node )
   }

   def apply[ S <: Sys[ S ]]( key: Int, targets: Mutating.Targets[ S ]) : ReactorSelector[ S ] =
      new MutatingTargetsSelector[ S ]( key, targets )

   def apply[ S <: Sys[ S ] /*, A */]( key: Int, node: Mutating[ S, _ /* A */]) : NodeSelector[ S ] = {
      new MutatingNodeSelector[ S /*, A, Mutating[ S, A ] */]( key, node )
   }

   private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Selector[ S ]] {
      def write( v: Selector[ S ], out: DataOutput ) {
         v.write( out )
      }

      def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Selector[ S ] = {
         // 0 = invariant, 1 = mutating, 2 = observer
         (in.readUnsignedByte(): @switch) match {
            case 0 =>
               val inlet   = in.readInt()
               val targets = Invariant.Targets.readAndExpand[ S ]( in, access )
               targets.select( inlet )
//               Selector( inlet, targets )
            case 1 =>
               val inlet   = in.readInt()
               val targets = Mutating.Targets.readAndExpand[ S ]( in, access )
               targets.select( inlet )
//               Selector( inlet, targets )
            case 2 =>
               val id = in.readInt()
               new ObserverKey[ S ]( id )
            case cookie => sys.error( "Unexpected cookie " + cookie )
         }
//         reactor.select( selector )
      }
   }

   private sealed trait TargetsSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
      final def nodeOption: Option[ NodeSelector[ S ]] = None
   }

   private final case class InvariantNodeSelector[ S <: Sys[ S ] /*, A, Repr <: Invariant[ S, A ] */]( inlet: Int, reactor: Invariant[ S, _ ] /* Repr */)
   extends NodeSelector[ S /*, A, Repr */ ] with InvariantSelector[ S ]

   private final case class InvariantTargetsSelector[ S <: Sys[ S ]]( inlet: Int, reactor: Invariant.Targets[ S ])
   extends TargetsSelector[ S ] with InvariantSelector[ S ]

   private final case class MutatingNodeSelector[ S <: Sys[ S ] /*, A, Repr <: Mutating[ S, A ] */]( inlet: Int, reactor: Mutating[ S, _ ] /* Repr */)
   extends NodeSelector[ S /*, A, Repr */] with MutatingSelector[ S ]

   private final case class MutatingTargetsSelector[ S <: Sys[ S ]]( inlet: Int, reactor: Mutating.Targets[ S ])
   extends TargetsSelector[ S ] with MutatingSelector[ S ]
}

sealed trait Selector[ S <: Sys[ S ]] extends Writer {
   protected def cookie: Int

   final def write( out: DataOutput ) {
      out.writeUnsignedByte( cookie )
      writeData( out )
   }

   protected def writeData( out: DataOutput ) : Unit

   private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) : Unit // ( implicit tx: S#Tx ) : Unit
   private[event] def toObserverKey : Option[ ObserverKey[ S ]] // Option[ Int ]
}

sealed trait ReactorSelector[ S <: Sys[ S ]] extends Selector[ S ] {
   def reactor: Reactor[ S ]
   def inlet: Int

   def nodeOption: Option[ NodeSelector[ S ]]

   final protected def writeData( out: DataOutput ) {
      out.writeInt( inlet )
      reactor.id.write( out )
   }

   override def hashCode : Int = {
      import MurmurHash._
      var h = startHash( 2 )
      val c = startMagicA
      val k = startMagicB
      h = extendHash( h, inlet, c, k )
      h = extendHash( h, reactor.id.##, nextMagicA( c ), nextMagicB( k ))
      finalizeHash( h )
   }

   override def equals( that: Any ) : Boolean = {
      (if( that.isInstanceOf[ ReactorSelector[ _ ]]) {
         val thatSel = that.asInstanceOf[ ReactorSelector[ _ ]]
         (inlet == thatSel.inlet && reactor.id == thatSel.reactor.id)
      } else super.equals( that ))
   }

   final private[event] def toObserverKey : Option[ ObserverKey[ S ]] = None

   override def toString = reactor.toString + ".select(" + inlet + ")"
}

sealed trait ExpandedSelector[ S <: Sys[ S ]] extends Selector[ S ] /* with Writer */ {
   private[event] def writeValue()( implicit tx: S#Tx ) : Unit
}

sealed trait InvariantSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
   protected def cookie: Int = 0
   final private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) { // ( implicit tx: S#Tx ) {
      push.visit( this, parent )
   }
}

sealed trait MutatingSelector[ S <: Sys[ S ]] extends ReactorSelector[ S ] {
   protected def cookie: Int = 1
   final private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) { // ( implicit tx: S#Tx ) {
      push.visit( this, parent )
   }
}

sealed trait NodeSelector[ S <: Sys[ S ] /*, A, Repr <: Node[ S, A ] */] extends ReactorSelector[ S ] with ExpandedSelector[ S ] {
   def reactor: Node[ S, _ ]

   final def nodeOption: Option[ NodeSelector[ S ]] = Some( this )

   final private[event] def pullUpdate( pull: Pull[ S ])( implicit tx: S#Tx ) : Option[ Any ] = {
      reactor.getEvent( inlet ).pullUpdate( pull )
   }

   final private[event] def writeValue()( implicit tx: S#Tx ) {
      tx.writeVal( reactor.id, reactor )
   }
}

/**
 * Instances of `ObserverKey` are provided by methods in `Txn`, when a live `Observer` is registered. Since
 * the observing function is not persisted, the key will be used for lookup (again through the transaction)
 * of the reacting function during the first reaction gathering phase of event propagation.
 */
final case class ObserverKey[ S <: Sys[ S ]] private[lucre] ( id: Int ) extends ExpandedSelector[ S ] {
   protected def cookie: Int = 2

   private[event] def toObserverKey : Option[ ObserverKey[ S ]] = Some( this )

   private[event] def pushUpdate( parent: ReactorSelector[ S ], push: Push[ S ]) { push.addLeaf( this, parent )}

   private[event] def writeValue()( implicit tx: S#Tx ) {}  // we are light weight, nothing to do here

   def dispose()( implicit tx: S#Tx ) {}  // XXX really?

   protected def writeData( out: DataOutput ) {
      out.writeInt( id )
   }
}

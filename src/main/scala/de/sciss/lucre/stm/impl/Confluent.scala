/*
 *  Confluent.scala
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

import stm.{ Txn => _Txn, Var => _Var }
import concurrent.stm.{InTxn, TxnExecutor}
import collection.immutable.{IntMap, IndexedSeq => IIdxSeq}
import scala.util.MurmurHash
import event.{ReactorSelector, Reactor, ObserverKey, ReactionMap, Reactions, Targets, Visited}

object Confluent {
   private type Acc = IIdxSeq[ Int ]
   private type S = Confluent

   private type M = Map[ Acc, Array[ Byte ]]

   sealed trait ID extends Identifier[ Txn ] {
      private[Confluent] def id: Int
      private[Confluent] def path: Acc
      final def shortString : String = path.mkString( "<", ",", ">" )

      override def hashCode = {
         import MurmurHash._
         var h = startHash( 2 )
         val c = startMagicA
         val k = startMagicB
         h = extendHash( h, id, c, k )
         h = extendHash( h, path.##, nextMagicA( c ), nextMagicB( k ))
         finalizeHash( h )
      }

      override def equals( that: Any ) : Boolean =
         that.isInstanceOf[ ID ] && {
            val b = that.asInstanceOf[ ID ]
            id == b.id && path == b.path
         }
   }

   sealed trait Txn extends _Txn[ S ]
   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ] {
      private[Confluent] def access( path: S#Acc )( implicit tx: S#Tx ) : A
   }

   def apply() : S = new System

   private final class System extends Confluent {
      private var cnt = 0
      private var pathVar = IIdxSeq.empty[ Int ]

      var storage = IntMap.empty[ M ]
      private val inMem = InMemory()

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, InMemory ]( inMem.atomic { implicit tx =>
         tx.newIntVar( tx.newID(), 0 )
      })( ctx => inMem.wrap( ctx.peer ))

      def path( implicit tx: Tx ) = pathVar

      def inPath[ Z ]( path: Acc )( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
            val oldPath = pathVar
            try {
               pathVar = path
               block( new TxnImpl( this, itx ))
            } finally {
               pathVar = oldPath
            }
         }
      }

      def fromPath[ A ]( path: Acc )( fun: Tx => A ) : A = {
         TxnExecutor.defaultAtomic[ A ] { itx =>
            pathVar = path :+ (pathVar.lastOption.getOrElse( -1 ) + 1)
            fun( new TxnImpl( this, itx ))
         }
      }

      def atomic[ A ]( fun: S#Tx => A ) : A = {
         TxnExecutor.defaultAtomic[ A ] { itx =>
            pathVar :+= (pathVar.lastOption.getOrElse( -1 ) + 1)
            fun( new TxnImpl( this, itx ))
         }
      }

//      def atomicAccess[ A ]( fun: (S#Tx, S#Acc) => A ) : A = {
//         TxnExecutor.defaultAtomic[ A ] { itx =>
//            pathVar :+= (pathVar.lastOption.getOrElse( -1 ) + 1)
//            fun( new TxnImpl( this, itx ), pathVar )
//         }
//      }

//      def atomicAccess[ A, B ]( source: S#Var[ A ])( fun: (S#Tx, A) => B ) : B = atomic { implicit tx =>
//         fun( tx, source.access( path ))
//      }

      def newIDCnt()( implicit tx: Tx ) : Int = {
         val id = cnt
         cnt += 1
         id
      }

      def newID()( implicit tx: Tx ) : ID = {
         val id = newIDCnt()
         new IDImpl( id, pathVar.takeRight( 1 ))
      }

      def update[ A <: Mutable[ S ]]( old: A )( implicit tx: Tx, reader: MutableReader[ ID, Txn, A ]) : A = {
         val out     = new DataOutput()
         old.write( out )
         val in      = new DataInput( out.toByteArray )
         val mid     = in.readInt()
         val newID   = IDImpl.readAndUpdate( mid, path, in )
         reader.readData( in, newID )
      }

      def manifest: Manifest[ S ] = Manifest.classType( classOf[ Confluent ])
   }

   private[Confluent] def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   private object IDImpl {
      def readPath( in: DataInput ) : Acc = {
         val sz      = in.readInt()
         IIdxSeq.fill( sz )( in.readInt() )
      }

      def readAndAppend( id: Int, postfix: Acc, in: DataInput ) : ID = {
         val path    = readPath( in )
         val newPath = path ++ postfix // accessPath.drop( com )
         new IDImpl( id, newPath )
      }

      def readAndReplace( id: Int, newPath: Acc, in: DataInput ) : ID = {
         readPath( in ) // just ignore it
         new IDImpl( id, newPath )
      }

      def readAndUpdate( id: Int, accessPath: Acc, in: DataInput ) : ID = {
         val sz      = in.readInt()
         val path    = IIdxSeq.fill( sz )( in.readInt() )
         val newPath = path :+ accessPath.last
         new IDImpl( id, newPath )
      }
   }
   private final class IDImpl( val id: Int, val path: Acc ) extends ID {
      def write( out: DataOutput ) {
         out.writeInt( id )
         out.writeInt( path.size )
         path.foreach( out.writeInt( _ ))
      }

      override def toString = "<"  + id + path.mkString( " @ ", ",", ">" )

      def dispose()( implicit tx: Txn ) {}
   }

   private final class TxnImpl( val system: System, val peer: InTxn ) extends Txn {
      def newID() : ID = system.newID()( this )

//      def addStateReaction[ A, Repr <: State[ S, A ]](
//         reader: State.Reader[ S, Repr ], fun: (Txn, A) => Unit ) : State.ReactorKey[ S ] =
//            system.reactionMap.addStateReaction( reader, fun )( this )
//
//      def mapStateTargets( in: DataInput, access: S#Acc, targets: State.Targets[ S ],
//                                               keys: IIdxSeq[ Int ]) : State.Reactor[ S ] =
//         system.reactionMap.mapStateTargets( in, access, targets, keys )( this )
//
//      def propagateState( key: Int, state: State[ S, _ ],
//                                            reactions: State.Reactions ) : State.Reactions =
//         system.reactionMap.propagateState( key, state, reactions )( this )
//
//      def removeStateReaction( key: State.ReactorKey[ S ]) { system.reactionMap.removeStateReaction( key )( this )}

      def addEventReaction[ A, Repr /* <: Event[ S, A ] */]( reader: event.Reader[ S, Repr, _ ],
                                                       fun: (S#Tx, A) => Unit ) : ObserverKey[ S ] =
         system.reactionMap.addEventReaction( reader, fun )( this )

      def mapEventTargets( in: DataInput, access: S#Acc, targets: Targets[ S ],
                           observers: IIdxSeq[ ObserverKey[ S ]]) : Reactor[ S ] =
         system.reactionMap.mapEventTargets( in, access, targets, observers )( this )

      def processEvent( observer: ObserverKey[ S ], update: Any, source: ReactorSelector[ S ], visited: Visited[ S ], reactions: Reactions ) {
         system.reactionMap.processEvent( observer, update, source, visited, reactions )( this )
      }

      def removeEventReaction( key: ObserverKey[ S ]) { system.reactionMap.removeEventReaction( key )( this )}

      def alloc( pid: ID )( implicit tx: Txn ) : ID = new IDImpl( system.newIDCnt(), pid.path )

      def newVar[ A ]( pid: ID, init: A )( implicit ser: TxnSerializer[ Txn, Acc, A ]) : Var[ A ] = {
         val id   = alloc( pid )( this )
         val res  = new VarImpl[ A ]( id, system, ser )
         res.store( init )
         res
      }

      def newBooleanVar( pid: ID, init: Boolean ) : Var[ Boolean ] = newVar[ Boolean ]( pid, init )
      def newIntVar(     pid: ID, init: Int ) :     Var[ Int ]     = newVar[ Int ](     pid, init )
      def newLongVar(    pid: ID, init: Long ) :    Var[ Long ]    = newVar[ Long ](    pid, init )

      def newVarArray[ A ]( size: Int ) = new Array[ Var[ A ]]( size )

      private def readSource( in: DataInput, pid: ID ) : ID = {
         val id = in.readInt()
         new IDImpl( id, pid.path )
      }

//      def read[ A ]( id: S#ID )( implicit reader: TxnReader[ S#Tx, S#Acc, A ]) : A = {
//         system.read ( id.id )( in => reader.read( in, () )( this ))( this )
//      }

      def readVar[ A ]( pid: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Acc, A ]) : Var[ A ] = {
         val id = readSource( in, pid )
         new VarImpl( id, system, ser )
      }

      def readBooleanVar( pid: ID, in: DataInput ) : Var[ Boolean ] = readVar[ Boolean ]( pid, in )
      def readIntVar(     pid: ID, in: DataInput ) : Var[ Int ]     = readVar[ Int ](     pid, in )
      def readLongVar(    pid: ID, in: DataInput ) : Var[ Long ]    = readVar[ Long ](    pid, in )

      def readID( in: DataInput, acc: Acc ) : ID = IDImpl.readAndAppend( in.readInt(), acc, in )

      def access[ A ]( source: S#Var[ A ]) : A = source.access( system.path( this ))( this )

//      def readMut[ A <: Mutable[ S ]]( pid: ID, in: DataInput )
//                                             ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
//         val mid  = in.readInt()
//         val id   = IDImpl.readAndReplace( mid, pid.path, in )
//         reader.readData( in, id )( this )
//      }
//
//      def readOptionMut[ A <: MutableOption[ S ]]( pid: ID, in: DataInput )
//                                                         ( implicit reader: MutableOptionReader[ ID, Txn, A ]) : A = {
//         val mid  = in.readInt()
//         if( mid == -1 ) reader.empty else {
//            val id   = IDImpl.readAndReplace( mid, pid.path, in )
//            reader.readData( in, id )( this )
//         }
//      }
   }

   private sealed trait SourceImpl[ @specialized A ] {
      protected def id: ID
      protected def system: System

      protected final def toString( pre: String ) = pre + id + ": " +
         (system.storage.getOrElse(id.id, Map.empty).map(_._1)).mkString( ", " )

      final def set( v: A )( implicit tx: Txn ) {
         store( v )
      }

      final def write( out: DataOutput ) {
         out.writeInt( id.id )
      }

      protected def writeValue( v: A, out: DataOutput ) : Unit
      protected def readValue( in: DataInput, postfix: Acc )( implicit tx: Txn ) : A

      final def store( v: A ) {
         val out = new DataOutput()
         writeValue( v, out )
         val bytes = out.toByteArray
         system.storage += id.id -> (system.storage.getOrElse( id.id,
            Map.empty[ Acc, Array[ Byte ]]) + (id.path -> bytes))
      }

      final def get( implicit tx: Txn ) : A = access( id.path )

      final def access( acc: S#Acc )( implicit tx: Txn ) : A = {
         var best: Array[Byte]   = null
         var bestLen = 0
         val map = system.storage.getOrElse( id.id, Map.empty )
         map.foreach {
            case (path, arr) =>
               val len = path.zip( acc ).segmentLength({ case (a, b) => a == b }, 0 )
               if( len > bestLen && len == path.size ) {
                  best     = arr
                  bestLen  = len
               }
         }
         require( best != null, "No value for path " + acc )
         val in = new DataInput( best )
         readValue( in, acc.drop( bestLen ))
      }

      final def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      final def dispose()( implicit tx: Txn ) {}
   }

   private final class VarImpl[ @specialized A ]( val id: ID, val system: System,
                                                  ser: TxnSerializer[ Txn, Acc, A ])
   extends Var[ A ] with SourceImpl[ A ] {

      override def toString = toString( "Var" )

      protected def writeValue( v: A, out: DataOutput ) {
         ser.write( v, out )
      }

      protected def readValue( in: DataInput, postfix: Acc )( implicit tx: Txn ) : A = {
         ser.read( in, postfix )
      }
   }
}

sealed trait Confluent extends Sys[ Confluent ] {
   import Confluent._

   type Var[ @specialized A ] = Confluent.Var[ A ]
   type ID                    = Confluent.ID
   type Tx                    = Confluent.Txn
   type Acc                   = IIdxSeq[ Int ]

   def inPath[ A ]( _path: Acc )( fun: Tx => A ) : A
   def fromPath[ A ]( _path: Acc )( fun: Tx => A ) : A
   def path( implicit tx: Tx ) : Acc
   def update[ A <: Mutable[ Confluent ]]( old: A )( implicit tx: Tx, reader: MutableReader[ ID, Txn, A ]) : A
}
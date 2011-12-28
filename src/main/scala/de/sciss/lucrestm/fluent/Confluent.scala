/*
 *  Confluent.scala
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
package fluent

import de.sciss.lucrestm.{ Txn => _Txn, Var => _Var }
import concurrent.stm.{InTxn, TxnExecutor}
import collection.immutable.{IntMap, IndexedSeq => IIdxSeq}

object Confluent {
   private type M = Map[ IIdxSeq[ Int ], Array[ Byte ]]

   sealed trait ID extends Identifier[ Txn ] {
      private[Confluent] def id: Int
      private[Confluent] def path: IIdxSeq[ Int ]
      final def shortString : String = path.mkString( "<", ",", ">" )
   }

   sealed trait Txn extends _Txn[ Confluent ]
   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ]

   def apply() : Confluent = new System

   private final class System extends Confluent {
      private var cnt = 0
      private var pathVar = IIdxSeq.empty[ Int ]

      var storage = IntMap.empty[ Map[ IIdxSeq[ Int ], Array[ Byte ]]]
      private val inMem = InMemory()

      val reactionMap: ReactionMap[ Confluent ] = ReactionMap[ Confluent, InMemory ]( inMem.atomic { implicit tx =>
         tx.newIntVar( tx.newID(), 0 )
      })( ctx => inMem.wrap( ctx.peer ))

      def path( implicit tx: Tx ) = pathVar

      def inPath[ Z ]( path: IIdxSeq[ Int ])( block: Tx => Z ) : Z = {
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

      def fromPath[ Z ]( path: IIdxSeq[ Int ])( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
            pathVar = path :+ (pathVar.lastOption.getOrElse( -1 ) + 1)
            block( new TxnImpl( this, itx ))
         }
      }

      def atomic[ Z ]( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
            pathVar :+= (pathVar.lastOption.getOrElse( -1 ) + 1)
            block( new TxnImpl( this, itx ))
         }
      }

      def newIDCnt()( implicit tx: Tx ) : Int = {
         val id = cnt
         cnt += 1
         id
      }

      def newID()( implicit tx: Tx ) : ID = {
         val id = newIDCnt()
         new IDImpl( id, pathVar.takeRight( 1 ))
      }

      def update[ A <: Mutable[ Confluent ]]( old: A )( implicit tx: Tx, reader: MutableReader[ ID, Txn, A ]) : A = {
         val out     = new DataOutput()
         old.write( out )
         val in      = new DataInput( out.toByteArray )
         val mid     = in.readInt()
         val newID   = IDImpl.readAndUpdate( mid, path, in )
         reader.readData( in, newID )
      }

      def manifest: Manifest[ Confluent ] = Manifest.classType( classOf[ Confluent ])
   }

   private[Confluent] def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   private object IDImpl {
      def readPath( in: DataInput ) : IIdxSeq[ Int ] = {
         val sz      = in.readInt()
         IIdxSeq.fill( sz )( in.readInt() )
      }

      def readAndAppend( id: Int, postfix: IIdxSeq[ Int ], in: DataInput ) : ID = {
         val path    = readPath( in )
         val newPath = path ++ postfix // accessPath.drop( com )
         new IDImpl( id, newPath )
      }

      def readAndReplace( id: Int, newPath: IIdxSeq[ Int ], in: DataInput ) : ID = {
         readPath( in ) // just ignore it
         new IDImpl( id, newPath )
      }

      def readAndUpdate( id: Int, accessPath: IIdxSeq[ Int ], in: DataInput ) : ID = {
         val sz      = in.readInt()
         val path    = IIdxSeq.fill( sz )( in.readInt() )
         val newPath = path :+ accessPath.last
         new IDImpl( id, newPath )
      }
   }
   private final class IDImpl( val id: Int, val path: IIdxSeq[ Int ]) extends ID {
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

      def addReaction( fun: Txn => Unit ) : StateReactorLeaf[ Confluent ] = system.reactionMap.addState( fun )( this )
      private[lucrestm] def removeReaction( key: Int ) { system.reactionMap.removeState( key )( this )}
      private[lucrestm] def invokeReaction( key: Int ) { system.reactionMap.invokeState( key )( this )}

      def alloc( pid: ID )( implicit tx: Txn ) : ID = new IDImpl( system.newIDCnt(), pid.path )

      def newVar[ A ]( pid: ID, init: A )( implicit ser: TxnSerializer[ Txn, IIdxSeq[ Int ], A ]) : Var[ A ] = {
         val id   = alloc( pid )( this )
         val res  = new VarImpl[ A ]( id, system, ser )
         res.store( init )
         res
      }

      def newIntVar( pid: ID, init: Int ) : Var[ Int ] = newVar[ Int ]( pid, init )
      def newLongVar( pid: ID, init: Long ) : Var[ Long ] = newVar[ Long ]( pid, init )

      def newVarArray[ A ]( size: Int ) = new Array[ Var[ A ]]( size )

      private def readSource( in: DataInput, pid: ID ) : ID = {
         val id = in.readInt()
         new IDImpl( id, pid.path )
      }

      def readVar[ A ]( pid: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, IIdxSeq[ Int ], A ]) : Var[ A ] = {
         val id = readSource( in, pid )
         new VarImpl( id, system, ser )
      }

      def readIntVar( pid: ID, in: DataInput ) : Var[ Int ] = readVar[ Int ]( pid, in )

      def readLongVar( pid: ID, in: DataInput ) : Var[ Long ] = readVar[ Long ]( pid, in )

      def readID( in: DataInput, acc: IIdxSeq[ Int ]) : ID = IDImpl.readAndAppend( in.readInt(), acc, in )

//      def readMut[ A <: Mutable[ Confluent ]]( pid: ID, in: DataInput )
//                                             ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
//         val mid  = in.readInt()
//         val id   = IDImpl.readAndReplace( mid, pid.path, in )
//         reader.readData( in, id )( this )
//      }
//
//      def readOptionMut[ A <: MutableOption[ Confluent ]]( pid: ID, in: DataInput )
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
      protected def readValue( in: DataInput, postfix: IIdxSeq[ Int ])( implicit tx: Txn ) : A

      final def store( v: A ) {
         val out = new DataOutput()
         writeValue( v, out )
         val bytes = out.toByteArray
         system.storage += id.id -> (system.storage.getOrElse( id.id,
            Map.empty[ IIdxSeq[ Int ], Array[ Byte ]]) + (id.path -> bytes))
      }

      final def get( implicit tx: Txn ) : A = {
         var best: Array[Byte]   = null
         var bestLen = 0
         val map = system.storage.getOrElse( id.id, Map.empty )
         map.foreach {
            case (path, arr) =>
               val len = path.zip( id.path ).segmentLength({ case (a, b) => a == b }, 0 )
               if( len > bestLen && len == path.size ) {
                  best     = arr
                  bestLen  = len
               }
         }
         require( best != null, "No value for path " + id.path )
         val in = new DataInput( best )
         readValue( in, id.path.drop( bestLen ))
      }

      final def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      final def dispose()( implicit tx: Txn ) {}
   }

   private final class VarImpl[ @specialized A ]( val id: ID, val system: System,
                                                  ser: TxnSerializer[ Txn, IIdxSeq[ Int ], A ])
   extends Var[ A ] with SourceImpl[ A ] {

      override def toString = toString( "Var" )

      protected def writeValue( v: A, out: DataOutput ) {
         ser.write( v, out )
      }

      protected def readValue( in: DataInput, postfix: IIdxSeq[ Int ])( implicit tx: Txn ) : A = {
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

   def inPath[ Z ]( _path: IIdxSeq[ Int ])( block: Tx => Z ) : Z
   def fromPath[ Z ]( _path: IIdxSeq[ Int ])( block: Tx => Z ) : Z
   def path( implicit tx: Tx ) : IIdxSeq[ Int ]
   def update[ A <: Mutable[ Confluent ]]( old: A )( implicit tx: Tx, reader: MutableReader[ ID, Txn, A ]) : A
}
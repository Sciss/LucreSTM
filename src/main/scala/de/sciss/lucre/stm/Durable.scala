/*
 *  Durable.scala
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

import stm.{Var => _Var, Txn => _Txn}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}
import annotation.elidable
import elidable.CONFIG
import event.ReactionMap
import LucreSTM.logConfig

object Durable {
   private type S = Durable

   sealed trait ID extends Identifier[ Txn ] {
      private[ Durable ] def id: Int
   }

   def apply( store: PersistentStore ) : S = {
      //         val idCnt   = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
      //            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
      //            in.readInt()
      //         } else 1
      //         kea( 3 )    = 1.toByte   // slot for react-last-slot
      //         val reactCnt = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
      //            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
      //            in.readInt()
      //         } else 0
      //         txn.commit()
      //         new System( env, db, txnCfg, ScalaRef( idCnt ), ScalaRef( reactCnt ))
      //      } catch {
      //         case e =>
      //            txn.abort()
      //            throw e
      //      }
      new System( store, 1, 0 )
   }

   def apply( factory: PersistentStoreFactory[ PersistentStore ], name: String = "data" ) : S =
      apply( factory.open( name ))

   private final class System( store: PersistentStore, idCnt0: Int, reactCnt0: Int )
   extends Durable {
      system =>

      private val idCnt    = ScalaRef( idCnt0 )
//      private val reactCnt = ScalaRef(reactCnt0)

      def manifest: Manifest[ S ] = Manifest.classType(classOf[ Durable ])

      private val idCntVar = new CachedIntVar( 0, idCnt )
      //      private val reactCntVar = new CachedIntVar( 1, idCnt )
      private val inMem = InMemory()

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, InMemory ]( inMem.step { implicit tx =>
         tx.newIntVar( tx.newID(), reactCnt0 )
      })( tx => inMem.wrap( tx.peer ))

      def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ] = v

      def root[ A ]( init: => A )( implicit tx: Txn, ser: TxnSerializer[ S#Tx, S#Acc, A ]): A = {
         val rootID = 2 // 1 == reaction map!!!
         if( exists( rootID )) {
            read( rootID )( ser.read( _, () ))
         } else {
            val id = newIDValue()
            require( id == rootID, "Root can only be initialized on an empty database (expected id count is " + rootID + " but found " + id + ")")
            val res = init
            write( id )( ser.write( res, _ ))
            res
         }
      }

      def exists( id: Int )( implicit tx: S#Tx ) : Boolean = store.contains( _.writeInt( id ))

      // ---- cursor ----

      def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx )))
      }

      def position( implicit tx: S#Tx ) : S#Acc = ()

      def position_=( path: S#Acc )( implicit tx: S#Tx ) {}

      //      def atomicAccess[ A ]( fun: (S#Tx, S#Acc) => A ) : A =
      //         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx ), () ))

      //      def atomicAccess[ A, B ]( source: S#Var[ A ])( fun: (S#Tx, A) => B ) : B = atomic { tx =>
      //         fun( tx, source.get( tx ))
      //      }

      def debugListUserRecords()( implicit tx: S#Tx ): Seq[ ID ] = {
         val b    = Seq.newBuilder[ ID ]
         val cnt  = idCnt.get( tx.peer )
         var i    = 1;
         while( i <= cnt ) {
            if( exists( i )) b += new IDImpl( i )
            i += 1
         }
         b.result()
      }

      def close() {
         store.close()
      }

      def wrap( peer: InTxn ) : S#Tx = new TxnImpl( this, peer )

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )

      def newIDValue()( implicit tx: Txn ) : Int = {
         val id = idCntVar.get( tx ) + 1
         logConfig( "new   <" + id + ">" )
         idCntVar.set( id )
         id
      }

      def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: Txn ) {
         logConfig( "write <" + id + ">" )
         store.put( _.writeInt( id ))( valueFun )
      }

      def remove( id: Int )( implicit tx: Txn ) {
         logConfig( "remov <" + id + ">" )
         store.remove( _.writeInt( id ))
      }

      def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: Txn ) : A = {
         logConfig( "read  <" + id + ">" )
         store.get( _.writeInt( id ))( valueFun ).getOrElse( sys.error( "Key not found " + id ))
      }

//      def tryRead[ A ]( id: Int )( valueFun: DataInput => A )( implicit tx: Txn ) : Option[ A ] = {
//         withIO { io =>
//            val in = io.read( id )
//            if( in != null ) Some( valueFun( in )) else None
//         }
//      }
   }

   private final class IDImpl( val id: Int ) extends ID {
      def write( out: DataOutput ) {
         out.writeInt( id )
      }

      override def hashCode: Int = id

      override def equals( that: Any ) : Boolean = {
         that.isInstanceOf[ IDImpl ] && (id == that.asInstanceOf[ IDImpl ].id)
      }

      def dispose()( implicit tx: Txn ) {
         tx.system.remove( id )
      }

      override def toString = "<" + id + ">"
   }

   private sealed trait BasicSource {
      protected def id: Int

      final def write( out: DataOutput ) {
         out.writeInt( id )
      }

      /* final */
      def dispose()( implicit tx: Txn ) {
         tx.system.remove( id )
      }

      @elidable(CONFIG) protected final def assertExists()(implicit tx: Txn) {
         require(tx.system.exists(id), "trying to write disposed ref " + id)
      }
   }

   //   private type Obs[ A ]    = Observer[ Txn, Change[ A ]]
   //   private type ObsVar[ A ] = Var[ A ] with State[ S, Change[ A ]]

   private sealed trait BasicVar[ A ] extends Var[ A ] with BasicSource {
      protected def ser: TxnSerializer[ Txn, Unit, A ]

      def get( implicit tx: Txn ) : A = tx.system.read[ A ]( id )( ser.read( _, () ))

      def setInit( v: A )( implicit tx: Txn ) { tx.system.write( id )( ser.write( v, _ ))}
   }

   private final class VarImpl[ A ]( protected val id: Int, protected val ser: TxnSerializer[ Txn, Unit, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( ser.write( v, _ ))
      }

      def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var(" + id + ")"
   }

   private final class BooleanVar( protected val id: Int )
   extends Var[ Boolean ] with BasicSource {
      def get( implicit tx: Txn ): Boolean = {
         tx.system.read[ Boolean ]( id )( _.readBoolean() )
      }

      def setInit( v: Boolean )( implicit tx: Txn ) {
         tx.system.write( id )( _.writeBoolean( v ))
      }

      def set( v: Boolean )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( _.writeBoolean( v ))
      }

      def transform( f: Boolean => Boolean )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var[Boolean](" + id + ")"
   }

   private final class IntVar( protected val id: Int )
   extends Var[ Int ] with BasicSource {
      def get( implicit tx: Txn ) : Int = {
         tx.system.read[ Int ]( id )( _.readInt() )
      }

      def setInit( v: Int )( implicit tx: Txn ) {
         tx.system.write( id )( _.writeInt( v ))
      }

      def set( v: Int )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( _.writeInt( v ))
      }

      def transform( f: Int => Int )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var[Int](" + id + ")"
   }

   private final class CachedIntVar( protected val id: Int, peer: ScalaRef[ Int ])
   extends Var[ Int ] with BasicSource {
      def get( implicit tx: Txn ) : Int = peer.get( tx.peer )

      def setInit( v: Int )( implicit tx: Txn ) { set( v )}

      def set( v: Int )( implicit tx: Txn ) {
         peer.set( v )( tx.peer )
         tx.system.write( id )( _.writeInt( v ))
      }

      def transform( f: Int => Int )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var[Int](" + id + ")"
   }

   private final class LongVar( protected val id: Int )
   extends Var[ Long ] with BasicSource {
      def get( implicit tx: Txn ) : Long = {
         tx.system.read[ Long ]( id )( _.readLong() )
      }

      def setInit( v: Long )( implicit tx: Txn ) {
         tx.system.write( id )( _.writeLong( v ))
      }

      def set( v: Long )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( _.writeLong( v ))
      }

      def transform( f: Long => Long )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var[Long](" + id + ")"
   }

   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ]

   sealed trait Txn extends _Txn[ S ]

   private final class TxnImpl( val system: System, val peer: InTxn )
   extends Txn {
      //      private var id = -1L

      def newID(): ID = new IDImpl( system.newIDValue()( this ))

      def reactionMap: ReactionMap[ S ] = system.reactionMap

      override def toString = "Txn" // <" + id + ">"

      def newVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]): Var[ A ] = {
         val res = new VarImpl[ A ]( system.newIDValue()( this ), ser )
         res.setInit( init )( this )
         res
      }

      def newBooleanVar( id: ID, init: Boolean ): Var[ Boolean ] = {
         val res = new BooleanVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newIntVar( id: ID, init: Int ): Var[ Int ] = {
         val res = new IntVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newLongVar( id: ID, init: Long ): Var[ Long ] = {
         val res = new LongVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newVarArray[ A ]( size: Int ) : Array[ Var[ A ] ] = new Array[ Var[ A ]]( size )

      def _readUgly[ A ]( parent: S#ID, id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A = {
         system.read( id.id )( serializer.read( _, () )( this ))( this )
      }

      def _writeUgly[ A ]( parent: S#ID, id: S#ID, value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) {
         system.write( id.id )( serializer.write( value, _ ))( this )
      }

      def readVal[ A ]( id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A = {
         system.read( id.id )( serializer.read( _, () )( this ))( this )
      }

      def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) {
         val idi = id.id
         if( !system.exists( idi )( this )) {
            system.write( idi )( serializer.write( value, _ ))( this )
         }
      }

      def readVar[ A ]( pid: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         val id = in.readInt()
         new VarImpl[ A ]( id, ser )
      }

      def readBooleanVar( pid: ID, in: DataInput ) : Var[ Boolean ] = {
         val id = in.readInt()
         new BooleanVar( id )
      }

      def readIntVar( pid: ID, in: DataInput ) : Var[ Int ] = {
         val id = in.readInt()
         new IntVar( id )
      }

      def readLongVar( pid: ID, in: DataInput ) : Var[ Long ] = {
         val id = in.readInt()
         new LongVar( id )
      }

      def readID( in: DataInput, acc: Unit ) : ID = new IDImpl( in.readInt() )

      def access[ A ]( source: S#Var[ A ]) : A = source.get( this )
   }
}

sealed trait Durable extends Sys[ Durable ] with Cursor[ Durable ] {
   final type Var[ @specialized A ] = Durable.Var[ A ]
   final type ID  = Durable.ID
   final type Tx  = Durable.Txn
   final type Acc = Unit
   final type Entry[ A ] = Durable.Var[ A ]

   /**
    * Closes the underlying database. The STM cannot be used beyond this call.
    */
   def close() : Unit

   /**
    * Reports the current number of records stored in the database.
    */
   def numRecords( implicit tx: Tx ) : Int

   /**
    * Reports the current number of user records stored in the database.
    * That is the number of records minus those records used for
    * database maintenance.
    */
   def numUserRecords( implicit tx: Tx ) : Int

   def debugListUserRecords()( implicit tx: Tx ) : Seq[ ID ]

   /**
    * Reads the root object representing the stored datastructure,
    * or provides a newly initialized one via the `init` argument,
    * if no root has been stored yet.
    */
   def root[ A ]( init: => A )( implicit tx: Tx, ser: TxnSerializer[ Tx, Acc, A ]) : A

   private[stm] def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: Tx ): A

   private[stm] def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: Tx ): Unit

   private[stm] def remove( id: Int )( implicit tx: Tx ) : Unit

   private[stm] def exists( id: Int )( implicit tx: Tx ) : Boolean

   private[stm] def newIDValue()( implicit tx: Tx ) : Int

   def wrap( peer: InTxn ) : Tx
}
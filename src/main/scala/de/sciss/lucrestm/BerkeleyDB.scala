/*
 *  BerkeleyDB.scala
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

import de.sciss.lucrestm.{Ref => _Ref, Val => _Val, Txn => _Txn}
import java.util.concurrent.ConcurrentLinkedQueue
import concurrent.stm.{Txn => ScalaTxn, InTxnEnd, TxnExecutor, InTxn, Ref => ScalaRef}
import java.io.{FileNotFoundException, File, IOException}
import com.sleepycat.je.{DatabaseEntry, DatabaseConfig, EnvironmentConfig, TransactionConfig, Environment, Database, Transaction, OperationStatus}
import annotation.elidable
import elidable.CONFIG

object BerkeleyDB {
   import LucreSTM.logConfig

   /* private val */ var DB_CONSOLE_LOG_LEVEL   = "OFF" // "ALL"

   sealed trait ID extends Identifier[ Txn ]

   def open( file: File, createIfNecessary: Boolean = true ) : BerkeleyDB = {
      val exists = file.isFile
      if( !exists && !createIfNecessary ) throw new FileNotFoundException( file.toString )

      val envCfg  = new EnvironmentConfig()
      val txnCfg  = new TransactionConfig()
      val dbCfg   = new DatabaseConfig()

      envCfg.setTransactional( true )
      envCfg.setAllowCreate( createIfNecessary )
      dbCfg.setTransactional( true )
      dbCfg.setAllowCreate( createIfNecessary )

      val dir     = file.getParentFile
      val name    = file.getName
      if( !exists ) dir.mkdirs()

//    envCfg.setConfigParam( EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL" )
      envCfg.setConfigParam( EnvironmentConfig.CONSOLE_LOGGING_LEVEL, DB_CONSOLE_LOG_LEVEL )
      val env     = new Environment( dir, envCfg )
      val txn     = env.beginTransaction( null, txnCfg )
      try {
         txn.setName( "Open '" + name + "'" )
         val db      = env.openDatabase( txn, name, dbCfg )
         val ke      = new DatabaseEntry( Array[ Byte ]( 0, 0, 0, 0 ))  // key for last-key
         val ve      = new DatabaseEntry()
         val cnt     = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
            in.readInt()
         } else 0
         txn.commit()
         new System( env, db, txnCfg, ScalaRef( cnt ))
      } catch {
         case e =>
            txn.abort()
            throw e
      }
   }

   private final class System( val env: Environment, db: Database, val txnCfg: TransactionConfig, idCnt: ScalaRef[ Int ])
   extends BerkeleyDB /* with ScalaTxn.ExternalDecider */ {
      system =>

      def manifest: Manifest[ BerkeleyDB ] = Manifest.classType( classOf[ BerkeleyDB ])

      private val ioQueue     = new ConcurrentLinkedQueue[ IO ]

      def root[ A ]( init: => A )( implicit tx: Txn, ser: Serializer[ A ]) : A = {
         val rootID = 1
         tryRead[ A ]( rootID )( ser.read( _ )).getOrElse {
            val id   = newIDValue()
            require( id == rootID, "Root can only be initialized on an empty database" )
            val res  = init
            write( id )( ser.write( res, _ ))
            res
         }
      }

      def atomic[ Z ]( block: Txn => Z ) : Z = TxnExecutor.defaultAtomic( itx => block( new TxnImpl( this, itx )))

      def debugListUserRecords()( implicit tx: Txn ) : Seq[ ID ] = {
         val b   = Seq.newBuilder[ ID ]
         val cnt = idCnt.get( tx.peer )
         var i = 1; while( i <= cnt ) {
            if( tryRead[ Unit ]( i )( _ => () ).isDefined ) b += new IDImpl( i )
         i += 1 }
         b.result()
      }

      def close() { db.close() }

      def numRecords : Long = db.count()
      def numUserRecords : Long = math.max( 0L, db.count() - 1 )

//      private def txnHandle( implicit txn: InTxnEnd ) : Transaction = dbTxnSTMRef.get

//      private def initDBTxn( implicit txn: InTxn ) : Transaction = {
//         ScalaTxn.setExternalDecider( this )
//         val dbTxn = env.beginTransaction( null, txnCfg )
//         logConfig( "txn begin <" + dbTxn.getId + ">" )
//         ScalaTxn.afterRollback { status =>
//            try {
//               logConfig( "txn rollback <" + dbTxn.getId + ">" )
//               dbTxn.abort()
//            } catch {
//               case _ =>
//            }
//         }
//         dbTxn
//      }

      def newIDValue()( implicit tx: Txn ) : Int = {
//      val id = idCnt.transformAndGet( _ + 1 )
         val itx = tx.peer
         val id  = idCnt.get( itx ) + 1
         logConfig( "new " + id )
         idCnt.set( id )( itx )
         withIO { io =>
            val out = io.beginWrite()
            out.writeInt( id )
            io.endWrite( 0 )
         }
         id
      }

      private def withIO[ A ]( fun: IO => A ) : A = {
         val ioOld   = ioQueue.poll()
         val io      = if( ioOld != null ) ioOld else new IO
         try {
            fun( io )
         } finally {
            ioQueue.offer( io )
         }
      }

      def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: Txn ) {
         logConfig( "write <" + id + ">" )
         withIO { io =>
            val out = io.beginWrite()
            valueFun( out )
            io.endWrite( id )
         }
      }

      def remove( id: Int )( implicit tx: Txn ) {
         logConfig( "remove <" + id + ">" )
         withIO( _.remove( id ))
      }

      def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: Txn ) : A = {
         logConfig( "read <" + id + ">" )
         withIO { io =>
            val in = io.read( id )
            if( in != null ) {
               valueFun( in )
            } else {
//            ScalaTxn.retry
               throw new IOException()
            }
         }
      }

      // XXX this can be implemented more efficient, using the no-data reading strategy of BDB
      def exists( id: Int )( implicit tx: Txn ) : Boolean = tryRead[ Unit ]( id )( _ => () ).isDefined

      def tryRead[ A ]( id: Int )( valueFun: DataInput => A )( implicit tx: Txn ) : Option[ A ] = {
//         logConfig( "try-read " + id )
         withIO { io =>
            val in = io.read( id )
            if( in != null ) Some( valueFun( in )) else None
         }
      }

      private final class IO {
         private val keyArr   = new Array[ Byte ]( 4 )
         private val keyE     = new DatabaseEntry( keyArr )
         private val valueE   = new DatabaseEntry()
         private val out      = new DataOutput()

         def beginWrite() : DataOutput = {
            out.reset()
            out
         }

         private def keyToArray( key: Int ) {
            val a    = keyArr
            a( 0 )   = (key >> 24).toByte
            a( 1 )   = (key >> 16).toByte
            a( 2 )   = (key >>  8).toByte
            a( 3 )   = key.toByte
         }

         def read( key: Int )( implicit tx: Txn ) : DataInput = {
            val h    = tx.dbTxn
            keyToArray( key )
            val ve   = valueE
            if( db.get( h, keyE, ve, null ) == OperationStatus.SUCCESS ) {
               new DataInput( ve.getData, ve.getOffset, ve.getSize )
            } else {
               null
            }
         }

         def remove( key: Int )( implicit tx: Txn ) {
            val h    = tx.dbTxn
            keyToArray( key )
            db.delete( h, keyE )
         }

         def endWrite( key: Int )( implicit tx: Txn ) {
            val h    = tx.dbTxn
            keyToArray( key )
            out.flush()
            valueE.setData( out.toByteArray )
            db.put( h, keyE, valueE )
         }
      }
   }

   private final class IDImpl( val id: Int ) extends ID {
      def write( out: DataOutput ) { out.writeInt( id )}

      override def equals( that: Any ) : Boolean = {
         /* (that != null) && */ that.isInstanceOf[ IDImpl ] && (id == that.asInstanceOf[ IDImpl ].id)
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

      final def dispose()( implicit tx: Txn ) {
         tx.system.remove( id )
      }

      @elidable(CONFIG) protected final def assertExists()( implicit tx: Txn ) {
         require( tx.system.exists( id ), "trying to write disposed ref " + id )
      }
   }

   private final class ValImpl[ A ]( protected val id: Int, ser: TxnSerializer[ Txn, A ])
   extends Val[ A ] with BasicSource {
      def get( implicit tx: Txn ) : A = {
         tx.system.read[ A ]( id )( ser.txnRead( _ ))
      }

      def setInit( v: A )( implicit tx: Txn ) {
         tx.system.write( id )( ser.write( v, _ ))
      }

      def set( v: A )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( ser.write( v, _ ))
      }

      def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Val(" + id + ")"
   }

   private final class IntVal( protected val id: Int )
   extends Val[ Int ] with BasicSource {
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

      override def toString = "Val[Int](" + id + ")"
   }

   private final class RefImpl[ A <: Mutable[ BerkeleyDB ]]( protected val id: Int,
                                                             val reader: MutableReader[ ID, Txn, A ])
   extends Ref[ A ] with BasicSource {
      override def toString = "Ref(" + id + ")"

//         def debug() {
//            println( toString )
//         }

      def get( implicit tx: Txn ) : A = {
         tx.system.read[ A ]( id ) { in =>
            val mid = in.readInt()
            reader.readData( in, new IDImpl( mid ))
         }
      }

      def setInit( v: A )( implicit tx: Txn ) {
         tx.system.write( id )( v.write( _ ))
      }

      def set( v: A )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( v.write( _ ))
      }

      def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}
   }

   private final class OptionRefImpl[ A <: MutableOption[ BerkeleyDB ]](
      protected val id: Int, val reader: MutableOptionReader[ ID, Txn, A ])
   extends Ref[ A ] with BasicSource {
      override def toString = "Ref(" + id + ")"

      def get( implicit tx: Txn ) : A = {
         tx.system.read[ A ]( id ) { in =>
            val mid = in.readInt()
            if( mid == -1 ) reader.empty else {
               reader.readData( in, new IDImpl( mid ))
            }
         }
      }

      def setInit( v: A )( implicit tx: Txn ) {
         tx.system.write( id ) { out =>
            v match {
               case m: Mutable[ _ ] => m.write( out )
               case _: EmptyMutable => out.writeInt( -1 )
            }
         }
      }

      def set( v: A )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id ) { out =>
            v match {
               case m: Mutable[ _ ] => m.write( out )
               case _: EmptyMutable => out.writeInt( -1 )
            }
         }
      }

      def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}
   }

   sealed trait Ref[ A ] extends _Ref[ Txn, A ]

   sealed trait Val[ @specialized A ] extends _Val[ Txn, A ]

   sealed trait Txn extends _Txn[ BerkeleyDB ] {
      private[BerkeleyDB] def dbTxn: Transaction
   }

   private final class TxnImpl( val system: System, val peer: InTxn )
   extends Txn with ScalaTxn.ExternalDecider {
      private var id = -1L

      def newID() : ID = new IDImpl( system.newIDValue()( this ))

      override def toString = "Txn<" + id + ">"

      lazy val dbTxn: Transaction = {
         ScalaTxn.setExternalDecider( this )( peer )
         val res = system.env.beginTransaction( null, system.txnCfg )
         id = res.getId
         logConfig( "txn begin <" + id + ">" )
         ScalaTxn.afterRollback({ status =>
            try {
               logConfig( "txn rollback <" + id + ">" )
               res.abort()
            } catch {
               case _ =>
            }
         })( peer )
         res
      }

      def newVal[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, A ]) : Val[ A ] = {
         val res = new ValImpl[ A ]( system.newIDValue()( this ), ser )
         res.setInit( init )( this )
         res
      }

      def newInt( id: ID, init: Int ) : Val[ Int ] = {
         val res = new IntVal( system.newIDValue()( this ) )
         res.setInit( init )( this )
         res
      }

      def newRef[ A <: Mutable[ BerkeleyDB ]]( id: ID, init: A )(
         implicit reader: MutableReader[ ID, Txn, A ]) : Ref[ A ] = {

         val res = new RefImpl[ A ]( system.newIDValue()( this ), reader )
         res.setInit( init )( this )
         res
      }

      def newOptionRef[ A <: MutableOption[ BerkeleyDB ]]( id: ID, init: A )(
         implicit reader: MutableOptionReader[ ID, Txn, A ]) : Ref[ A ] = {

         val res = new OptionRefImpl[ A ]( system.newIDValue()( this ), reader )
         res.setInit( init )( this )
         res
      }

      def newValArray[ A ]( size: Int ) : Array[ Val[ A ]] = new Array[ Val[ A ]]( size )

      def newRefArray[ A ]( size: Int ) : Array[ Ref[ A ]] = new Array[ Ref[ A ]]( size )

      def readVal[ A ]( pid: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, A ]) : Val[ A ] = {
         val id = in.readInt()
         new ValImpl[ A ]( id, ser )
      }

      def readInt( pid: ID, in: DataInput ) : Val[ Int ] = {
         val id = in.readInt()
         new IntVal( id )
      }

      def readRef[ A <: Mutable[ BerkeleyDB ]]( pid: ID, in: DataInput )
                                              ( implicit reader: MutableReader[ ID, Txn, A ]) : Ref[ A ] = {
         val id = in.readInt()
         new RefImpl[ A ]( id, reader )
      }

      def readOptionRef[ A <: MutableOption[ BerkeleyDB ]]( pid: ID, in: DataInput )(
         implicit reader: MutableOptionReader[ ID, Txn, A ]) : Ref[ A ] = {

         val id = in.readInt()
         new OptionRefImpl[ A ]( id, reader )
      }

      def readMut[ A <: Mutable[ BerkeleyDB ]]( pid: ID, in: DataInput )
                                              ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
         val id = new IDImpl( in.readInt() )
         reader.readData( in, id )( this )
      }

      def readOptionMut[ A <: MutableOption[ BerkeleyDB ]]( pid: ID, in: DataInput )
                                                          ( implicit reader: MutableOptionReader[ ID, Txn, A ]) : A = {
         val mid = in.readInt()
         if( mid == -1 ) reader.empty else {
            reader.readData( in, new IDImpl( mid ))( this )
         }
      }


      // ---- ExternalDecider ----
      def shouldCommit( implicit txn: InTxnEnd ) : Boolean = {
         try {
            logConfig( "txn commit <" + dbTxn.getId + ">" )
            dbTxn.commit()
            true
         } catch {
            case e =>
               try {
                  logConfig( "txn abort <" + dbTxn.getId + ">" )
                  dbTxn.abort()
               } catch {
                  case _ =>
               }
               false
         }
      }
   }
}
sealed trait BerkeleyDB extends Sys[ BerkeleyDB ] {
   type Val[ @specialized A ] = BerkeleyDB.Val[ A ]
   type Ref[ A ]  = BerkeleyDB.Ref[ A ]
//   type Mut[ +A ] = BerkeleyDB.Mut[ A ]
   type ID        = BerkeleyDB.ID
   type Tx        = BerkeleyDB.Txn // InTxn

   /**
    * Closes the underlying database. The STM cannot be used beyond this call.
    */
   def close() : Unit

   /**
    * Reports the current number of records stored in the database.
    */
   def numRecords: Long

   /**
    * Reports the current number of user records stored in the database.
    * That is the number of records minus those records used for
    * database maintenance.
    */
   def numUserRecords : Long

   def debugListUserRecords()( implicit tx: Tx ) : Seq[ ID ]

   /**
    * Reads the root object representing the stored datastructure,
    * or provides a newly initialized one via the `init` argument,
    * if no root has been stored yet.
    */
   def root[ A ]( init: => A )( implicit tx: Tx, ser: Serializer[ A ]) : A

   private[lucrestm] def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: BerkeleyDB#Tx ) : A
   private[lucrestm] def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: BerkeleyDB#Tx ) : Unit
   private[lucrestm] def remove( id: Int )( implicit tx: BerkeleyDB#Tx ) : Unit
   private[lucrestm] def exists( id: Int )( implicit tx: BerkeleyDB#Tx ) : Boolean
   private[lucrestm] def newIDValue()( implicit tx: BerkeleyDB#Tx ) : Int
}
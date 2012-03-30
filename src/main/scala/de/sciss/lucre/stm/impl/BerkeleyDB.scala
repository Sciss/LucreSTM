/*
 *  BerkeleyDB.scala
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

import de.sciss.lucre.stm.DataStore
import java.util.concurrent.ConcurrentLinkedQueue
import LucreSTM.logConfig
import concurrent.stm.{InTxnEnd, TxnLocal, Txn => ScalaTxn}
import com.sleepycat.je.{OperationStatus, LockMode, DatabaseEntry, Database, Environment, DatabaseConfig, TransactionConfig, EnvironmentConfig}
import java.io.{File, FileNotFoundException}
import OperationStatus.SUCCESS

object BerkeleyDB {
   sealed trait LogLevel
   case object LogOff extends LogLevel { override def toString = "OFF" }
   case object LogAll extends LogLevel { override def toString = "ALL" }

   def factory( dir: File, createIfNecessary: Boolean = true,
                logLevel: LogLevel = LogOff ) : DataStoreFactory[ BerkeleyDB ] =
      new Factory( dir, createIfNecessary, logLevel )

   private final class Factory( dir: File, createIfNecessary: Boolean, logLevel: LogLevel )
   extends DataStoreFactory[ BerkeleyDB ] {
      private lazy val env = {
         val envCfg  = new EnvironmentConfig()
         val txnCfg  = new TransactionConfig()

         envCfg.setTransactional( true )
         envCfg.setAllowCreate(   createIfNecessary )

   //    envCfg.setConfigParam( EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL" )
         envCfg.setConfigParam( EnvironmentConfig.CONSOLE_LOGGING_LEVEL, logLevel.toString )
         val env = new Environment( dir, envCfg )
         new Env( env, txnCfg )
      }

      def open( name: String ) : BerkeleyDB = {
         val exists = dir.isDirectory
         if( !exists && !createIfNecessary ) throw new FileNotFoundException( dir.toString )
         if( !exists ) dir.mkdirs()

         val dbCfg   = new DatabaseConfig()
         dbCfg.setTransactional( true )
         dbCfg.setAllowCreate( createIfNecessary )

         val txn = env.env.beginTransaction( null, env.txnCfg )
         try {
            txn.setName( "Open '" + name + "'" )
            val db      = env.env.openDatabase( txn, name, dbCfg )
   //         val kea     = Array[ Byte ]( 0, 0, 0, 0 )
   //         val ke      = new DatabaseEntry( kea )  // slot for last-slot
   //         val ve      = new DatabaseEntry()
   //         val idCnt   = if( db.get( txn, ke, ve, null ) == SUCCESS ) {
   //            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
   //            in.readInt()
   //         } else 1
   //         kea( 3 )    = 1.toByte   // slot for react-last-slot
   //         val reactCnt = if( db.get( txn, ke, ve, null ) == SUCCESS ) {
   //            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
   //            in.readInt()
   //         } else 0
            txn.commit()
            new Impl( env, db )
         } catch {
            case e =>
               txn.abort()
               throw e
         }
      }
   }

   def open( dir: File, name: String = "data", createIfNecessary: Boolean = true,
             logLevel: LogLevel = LogOff ) : BerkeleyDB =
      factory( dir, createIfNecessary, logLevel ).open( name )

   private final class Impl( env: Env, db: Database )
   extends BerkeleyDB {
      def put( keyFun: DataOutput => Unit )( valueFun: DataOutput => Unit )( implicit tx: Txn[ _ ]) {
         env.put( keyFun, valueFun, db )
      }

      def get[ A ]( keyFun: DataOutput => Unit )( valueFun: DataInput => A )( implicit tx: Txn[ _ ]) : Option[ A ] =
         env.get[ A ]( keyFun, valueFun, db )

      def flatGet[ A ]( keyFun: DataOutput => Unit )( valueFun: DataInput => Option[ A ])( implicit tx: Txn[ _ ]) : Option[ A ] =
         env.flatGet[ A ]( keyFun, valueFun, db )

      def contains( keyFun: DataOutput => Unit )( implicit tx: Txn[ _ ]) : Boolean =
         env.contains( keyFun, db )

      def remove( keyFun: DataOutput => Unit )( implicit tx: Txn[ _ ]) : Boolean =
         env.remove( keyFun, db )

      def close() { db.close() }

      def numEntries( implicit tx: Txn[ _ ]) : Int = db.count().toInt
   }

   private final class Env( val env: Environment, val txnCfg: TransactionConfig )
   extends ScalaTxn.ExternalDecider {
      private val ioQueue     = new ConcurrentLinkedQueue[ IO ]
      private val dbTxnRef    = TxnLocal( initialValue = { implicit tx =>
         ScalaTxn.setExternalDecider( this )
         val res  = env.beginTransaction( null, txnCfg )
         val id   = res.getId
         logConfig( "txn begin  <" + id + ">" )
         ScalaTxn.afterRollback({ status =>
            try {
               logConfig( "txn rollback <" + id + ">" )
               res.abort()
            } catch {
               case _ =>
            }
         })
         res
      })

      // ---- ExternalDecider ----
      def shouldCommit( implicit txn: InTxnEnd ) : Boolean = {
         val dbTxn = dbTxnRef()
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

      private def withIO[ A ]( fun: IO => A ) : A = {
         val ioOld   = ioQueue.poll()
         val io      = if( ioOld != null ) ioOld else new IO
         try {
            fun( io )
         } finally {
            ioQueue.offer( io )
         }
      }

      def put( keyFun: DataOutput => Unit, valueFun: DataOutput => Unit, db: Database )( implicit tx: Txn[ _ ]) {
         withIO { io =>
            val out        = io.out
            val keyE       = io.keyE
            val valueE     = io.valueE

            out.reset()
            keyFun( out )
            val keySize    = out.getBufferLength
            valueFun( out )
            val valueSize  = out.getBufferLength - keySize
            val data       = out.getBufferBytes
            keyE.setData(   data, 0,       keySize   )
            valueE.setData( data, keySize, valueSize )
            db.put( dbTxnRef()( tx.peer ), keyE, valueE )
         }
      }

      def get[ A ]( keyFun: DataOutput => Unit, valueFun: DataInput => A, db: Database )( implicit tx: Txn[ _ ]) : Option[ A ] = {
         withIO { io =>
            val out        = io.out
            val keyE       = io.keyE
            val valueE     = io.valueE

            out.reset()
            keyFun( out )
            val keySize    = out.getBufferLength
            val data       = out.getBufferBytes
            keyE.setData( data, 0, keySize )
            if( db.get( dbTxnRef()( tx.peer ), keyE, valueE, LockMode.DEFAULT ) == SUCCESS ) {
               val in = new DataInput( valueE.getData, valueE.getOffset, valueE.getSize )
               Some( valueFun( in ))
            } else {
               None
            }
         }
      }

      def flatGet[ A ]( keyFun: DataOutput => Unit, valueFun: DataInput => Option[ A ], db: Database )( implicit tx: Txn[ _ ]) : Option[ A ] = {
         withIO { io =>
            val out        = io.out
            val keyE       = io.keyE
            val valueE     = io.valueE

            out.reset()
            keyFun( out )
            val keySize    = out.getBufferLength
            val data       = out.getBufferBytes
            keyE.setData( data, 0, keySize )
            if( db.get( dbTxnRef()( tx.peer ), keyE, valueE, LockMode.DEFAULT ) == SUCCESS ) {
               val in = new DataInput( valueE.getData, valueE.getOffset, valueE.getSize )
               valueFun( in )
            } else {
               None
            }
         }
      }

      def contains( keyFun: DataOutput => Unit, db: Database )( implicit tx: Txn[ _ ]) : Boolean = {
         withIO { io =>
            val out        = io.out
            val keyE       = io.keyE
            val partialE   = io.partialE

            out.reset()
            keyFun( out )
            val keySize    = out.getBufferLength
            val data       = out.getBufferBytes
            keyE.setData( data, 0, keySize )
            db.get( dbTxnRef()( tx.peer ), keyE, partialE, LockMode.READ_UNCOMMITTED ) == SUCCESS
         }
      }

      def remove( keyFun: DataOutput => Unit, db: Database )( implicit tx: Txn[ _ ]) : Boolean = {
         withIO { io =>
            val out        = io.out
            val keyE       = io.keyE

            out.reset()
            keyFun( out )
            val keySize    = out.getBufferLength
            val data       = out.getBufferBytes
            keyE.setData( data, 0, keySize )
            db.delete( dbTxnRef()( tx.peer ), keyE ) == SUCCESS
         }
      }
   }

   private[BerkeleyDB] final class IO {
      val keyE     = new DatabaseEntry()
      val valueE   = new DatabaseEntry()
      val partialE = new DatabaseEntry()
      val out      = new DataOutput()

      partialE.setPartial( 0, 0, true )
   }
}
trait BerkeleyDB extends DataStore
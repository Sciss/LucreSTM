/*
 *  BerkeleyDB.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2013 Hanns Holger Rutz. All rights reserved.
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
package store

import de.sciss.lucre.stm.DataStore
import java.util.concurrent.ConcurrentLinkedQueue
import concurrent.stm.{InTxnEnd, TxnLocal, Txn => ScalaTxn}
import com.sleepycat.je.{Transaction, OperationStatus, LockMode, DatabaseEntry, Database, Environment, DatabaseConfig, TransactionConfig, EnvironmentConfig}
import java.io.{File, FileNotFoundException}
import OperationStatus.SUCCESS
import concurrent.stm.Txn.ExternalDecider
import util.control.NonFatal

object BerkeleyDB {
  sealed trait LogLevel
  case object LogOff extends LogLevel { override def toString = "OFF" }
  case object LogAll extends LogLevel { override def toString = "ALL" }

  def tmp(logLevel: LogLevel = LogOff): DataStoreFactory[BerkeleyDB] = {
    val dir = File.createTempFile("sleepycat_", "db")
    dir.delete()
    BerkeleyDB.factory(dir, logLevel = logLevel)
  }

  def factory(dir: File, createIfNecessary: Boolean = true,
              logLevel: LogLevel = LogOff): DataStoreFactory[BerkeleyDB] =
    new Factory(dir, createIfNecessary, logLevel)

  private final class Factory(dir: File, createIfNecessary: Boolean, logLevel: LogLevel)
    extends DataStoreFactory[BerkeleyDB] {

    private /* lazy */ val txe: TxEnv = {
      val envCfg = new EnvironmentConfig()
      val txnCfg = new TransactionConfig()

      envCfg.setTransactional(true)
      envCfg.setAllowCreate(createIfNecessary)

      //    envCfg.setConfigParam( EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL" )
      envCfg.setConfigParam(EnvironmentConfig.CONSOLE_LOGGING_LEVEL, logLevel.toString)
      val env = new Environment(dir, envCfg)
      new TxEnv(env, txnCfg)
    }

    def open(name: String, overwrite: Boolean): BerkeleyDB = {
      val exists = dir.isDirectory
      if (!exists && !createIfNecessary) throw new FileNotFoundException(dir.toString)
      if (!exists) dir.mkdirs()

      val dbCfg = new DatabaseConfig()
      dbCfg.setTransactional(true)
      dbCfg.setAllowCreate(createIfNecessary)

      val e = txe.env
      val txn = e.beginTransaction(null, txe.txnCfg)
      try {
        txn.setName("Open '" + name + "'")
        if (overwrite && e.getDatabaseNames.contains(name)) {
          e.truncateDatabase(txn, name, false)
        }
        val db = e.openDatabase(txn, name, dbCfg)
        txn.commit()
        new Impl(txe, db)
      } catch {
        case err: Throwable =>
          txn.abort()
          throw err
      }
    }
  }

  def open(dir: File, name: String = "data", createIfNecessary: Boolean = true,
           logLevel: LogLevel = LogOff): BerkeleyDB =
    factory(dir, createIfNecessary, logLevel).open(name)

  private final class Impl(txe: TxEnv, db: Database)
    extends BerkeleyDB {
    def put(keyFun: DataOutput => Unit)(valueFun: DataOutput => Unit)(implicit tx: TxnLike) {
      txe.withIO { (io, dbTxn) =>
        val out     = io.out
        val keyE    = io.keyE
        val valueE  = io.valueE

        out.reset()
        keyFun(out)
        val keySize = out.getBufferLength
        valueFun(out)
        val valueSize = out.getBufferLength - keySize
        val data = out.getBufferBytes
        keyE.setData(data, 0, keySize)
        valueE.setData(data, keySize, valueSize)
        db.put(dbTxn, keyE, valueE)
      }
    }

    def get[A](keyFun: DataOutput => Unit)(valueFun: DataInput => A)(implicit tx: TxnLike): Option[A] = {
      txe.withIO { (io, dbTxn) =>
        val out     = io.out
        val keyE    = io.keyE
        val valueE  = io.valueE

        out.reset()
        keyFun(out)
        val keySize = out.getBufferLength
        val data = out.getBufferBytes
        keyE.setData(data, 0, keySize)
        if (db.get(dbTxn, keyE, valueE, LockMode.DEFAULT) == SUCCESS) {
          val in = new DataInput(valueE.getData, valueE.getOffset, valueE.getSize)
          Some(valueFun(in))
        } else {
          None
        }
      }
    }

    def flatGet[A](keyFun: DataOutput => Unit)(valueFun: DataInput => Option[A])(implicit tx: TxnLike): Option[A] = {
      txe.withIO { (io, dbTxn) =>
        val out     = io.out
        val keyE    = io.keyE
        val valueE  = io.valueE

        out.reset()
        keyFun(out)
        val keySize = out.getBufferLength
        val data    = out.getBufferBytes
        keyE.setData(data, 0, keySize)
        if (db.get(dbTxn, keyE, valueE, LockMode.DEFAULT) == SUCCESS) {
          val in = new DataInput(valueE.getData, valueE.getOffset, valueE.getSize)
          valueFun(in)
        } else {
          None
        }
      }
    }

    def contains(keyFun: DataOutput => Unit)(implicit tx: TxnLike): Boolean = {
      txe.withIO { (io, dbTxn) =>
        val out       = io.out
        val keyE      = io.keyE
        val partialE  = io.partialE

        out.reset()
        keyFun(out)
        val keySize   = out.getBufferLength
        val data      = out.getBufferBytes
        keyE.setData(data, 0, keySize)
        db.get(dbTxn, keyE, partialE, LockMode.READ_UNCOMMITTED) == SUCCESS
      }
    }

    def remove(keyFun: DataOutput => Unit)(implicit tx: TxnLike): Boolean = {
      txe.withIO { (io, dbTxn) =>
        val out       = io.out
        val keyE      = io.keyE

        out.reset()
        keyFun(out)
        val keySize   = out.getBufferLength
        val data      = out.getBufferBytes
        keyE.setData(data, 0, keySize)
        db.delete(dbTxn, keyE) == SUCCESS
      }
    }

    def close() {
      db.close()
    }

    def numEntries(implicit tx: TxnLike): Int = db.count().toInt
  }

  private final class TxEnv(val env: Environment, val txnCfg: TransactionConfig) extends ExternalDecider {
    private val ioQueue   = new ConcurrentLinkedQueue[IO]
    private val dbTxnRef  = TxnLocal(initialValue = { implicit tx =>
      ScalaTxn.setExternalDecider(this)
      val res = env.beginTransaction(null, txnCfg)
      val id  = res.getId
      log("txn begin  <" + id + ">")
      ScalaTxn.afterRollback {
        case ScalaTxn.RolledBack(cause) =>
          log("txn rollback <" + id + ">")
          // currently, it seems Scala-STM swallows the uncaught exception as soon
          // as we have registered this afterRollback handler. As a remedy, we'll
          // explicitly print that exception trace.
          cause match {
            case ScalaTxn.UncaughtExceptionCause(e) => e.printStackTrace()
            case _ =>
          }
          res.abort()
        case _ => // shouldn't happen since this is afterRollback?
      }
      res
    })


    def shouldCommit(implicit txn: InTxnEnd): Boolean = {
      val dbTxn = dbTxnRef()
      try {
        log("txn commit <" + dbTxn.getId + ">")
        dbTxn.commit()
        true
      } catch {
        case NonFatal(e) =>
          e.printStackTrace()
          log("txn abort <" + dbTxn.getId + ">")
          dbTxn.abort()
          false
      }
    }

    def withIO[A](fun: (IO, Transaction) => A)(implicit tx: TxnLike): A = {
      val ioOld = ioQueue.poll()
      val io    = if (ioOld != null) ioOld else new IO
      val dbTxn = dbTxnRef()(tx.peer)
      try {
        fun(io, dbTxn)
      } finally {
        ioQueue.offer(io)
      }
    }
  }

  private[BerkeleyDB] final class IO {
    val keyE      = new DatabaseEntry()
    val valueE    = new DatabaseEntry()
    val partialE  = new DatabaseEntry()
    val out       = new DataOutput()

    partialE.setPartial(0, 0, true)
  }
}
trait BerkeleyDB extends DataStore
/*
 *  BerkeleyDB.scala
 *  (LucreSTM-BDB6)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package lucre
package stm
package store

import java.io.{File, FileNotFoundException}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

import com.sleepycat.je.OperationStatus.SUCCESS
import com.sleepycat.je.{LockMode, DatabaseEntry, Transaction, Database, DatabaseConfig, Environment, EnvironmentConfig, TransactionConfig}
import de.sciss.serial.{DataInput, DataOutput}

import scala.annotation.meta.field
import scala.concurrent.duration.Duration
import scala.concurrent.stm.Txn.ExternalDecider
import scala.concurrent.stm.{InTxnEnd, TxnLocal, Txn => ScalaTxn}
import scala.language.implicitConversions
import scala.util.control.NonFatal

object BerkeleyDB {
  sealed trait LogLevells -la
  case object LogOff extends LogLevel { override def toString = "OFF" }
  case object LogAll extends LogLevel { override def toString = "ALL" }

  sealed trait ConfigLike {
    def logLevel    : LogLevel

    def readOnly    : Boolean
    def allowCreate : Boolean
    def sharedCache : Boolean

    def txnTimeout  : Duration
    def lockTimeout : Duration
  }
  object Config {
    def apply(): ConfigBuilder = new ConfigBuilder()
    implicit def build(b: ConfigBuilder): Config = b.build
  }
  trait Config extends ConfigLike
  object ConfigBuilder {
    def apply(config: Config): ConfigBuilder = {
      val b = new ConfigBuilder
      b.read(config)
      b
    }
  }
  final class ConfigBuilder private[BerkeleyDB] () extends ConfigLike {
    var logLevel    : LogLevel = LogOff

    var readOnly    : Boolean  = false
    var allowCreate : Boolean  = true
    var sharedCache : Boolean  = false

    var txnTimeout  : Duration = Duration(  0, TimeUnit.MILLISECONDS)
    var lockTimeout : Duration = Duration(500, TimeUnit.MILLISECONDS)

    def read(config: Config): Unit = {
      this.logLevel     = config.logLevel
      this.readOnly     = config.readOnly
      this.allowCreate  = config.allowCreate
      this.sharedCache  = config.sharedCache
      this.txnTimeout   = config.txnTimeout
      this.lockTimeout  = config.lockTimeout
    }

    def build: Config = new ConfigImpl(logLevel = logLevel, readOnly = readOnly, allowCreate = allowCreate,
                                       sharedCache = sharedCache, txnTimeout = txnTimeout, lockTimeout = lockTimeout)
  }
  private final case class ConfigImpl(logLevel: LogLevel, readOnly: Boolean, allowCreate: Boolean,
                                      sharedCache: Boolean, txnTimeout: Duration, lockTimeout: Duration)
    extends Config

  def tmp(logLevel: LogLevel = LogOff): DataStoreFactory[BerkeleyDB] = {
    val config = Config()
    config.logLevel  = logLevel
    tmp(config.build)
  }

  def tmp(config: Config): DataStoreFactory[BerkeleyDB] = {
    val dir = File.createTempFile("sleepycat_", "db")
    dir.delete()
    BerkeleyDB.factory(dir, config)
  }

  def factory(dir: File, createIfNecessary: Boolean = true,
              logLevel: LogLevel = LogOff): DataStoreFactory[BerkeleyDB] = {
    val config = Config()
    config.allowCreate  = createIfNecessary
    factory(dir, config.build)
  }

  def factory(dir: File, config: Config): DataStoreFactory[BerkeleyDB] = {
    val exists = dir.isDirectory
    if (!exists && !config.allowCreate) throw new FileNotFoundException(dir.toString)
    if (!exists) dir.mkdirs()
    new Factory(dir, config)
  }

  def open(dir: File, name: String = "data", createIfNecessary: Boolean = true,
           logLevel: LogLevel = LogOff): BerkeleyDB =
    factory(dir, createIfNecessary, logLevel).open(name)

  private final class Factory(dir: File, config: Config)
    extends DataStoreFactory[BerkeleyDB] {

    // legacy
    def this(dir: File, allowCreate: Boolean, logLevel: LogLevel) = this(dir, {
      val config = Config()
      config.logLevel    = logLevel
      config.allowCreate = allowCreate
      config.build
    })

    private[this] /* lazy */ val txe: TxEnv = {
      val envCfg = new EnvironmentConfig()
      val txnCfg = new TransactionConfig()

      envCfg.setTransactional(true)
      envCfg.setAllowCreate(config.allowCreate)
      envCfg.setReadOnly   (config.readOnly   )
      envCfg.setSharedCache(config.sharedCache)
      envCfg.setTxnTimeout (config.txnTimeout .length, config .txnTimeout.unit)
      envCfg.setLockTimeout(config.lockTimeout.length, config.lockTimeout.unit)

      // val mCfg = new EnvironmentMutableConfig()
      // mCfg.set

      envCfg.setConfigParam(EnvironmentConfig.CLEANER_MIN_FILE_UTILIZATION, "33")

      //    envCfg.setConfigParam( EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL" )
      envCfg.setConfigParam(EnvironmentConfig.CONSOLE_LOGGING_LEVEL, config.logLevel.toString)
      val env = new Environment(dir, envCfg)

      new TxEnv(env, txnCfg)
    }

    def open(name: String, overwrite: Boolean): BerkeleyDB = {
//      val exists = dir.isDirectory
//      if (!exists && !createIfNecessary) throw new FileNotFoundException(dir.toString)
//      if (!exists) dir.mkdirs()

      val dbCfg = new DatabaseConfig()
      dbCfg.setTransactional(true)
      dbCfg.setAllowCreate(config.allowCreate || overwrite)
      dbCfg.setReadOnly   (config.readOnly   )
      // if (overwrite) dbCfg.setTemporary(true)

      val e   = txe.env
      val txn = e.beginTransaction(null, txe.txnCfg)
      try {
        txn.setName(s"Open '$name'")
        if (overwrite && e.getDatabaseNames.contains(name)) {
          // e.truncateDatabase(txn, name, false)
          e.removeDatabase(txn, name)
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

  private final class Impl(txe: TxEnv, db: Database)
    extends BerkeleyDB {
    def put(keyFun: DataOutput => Unit)(valueFun: DataOutput => Unit)(implicit tx: TxnLike): Unit =
      txe.withIO { (io, dbTxn) =>
        val out     = io.out
        val keyE    = io.keyE
        val valueE  = io.valueE

        out.reset()
        keyFun(out)
        val keySize = out.position // size
        valueFun(out)
        val valueSize = out.size - keySize
        val data = out.buffer
        keyE.setData(data, 0, keySize)
        valueE.setData(data, keySize, valueSize)
        db.put(dbTxn, keyE, valueE)
      }

    def get[A](keyFun: DataOutput => Unit)(valueFun: DataInput => A)(implicit tx: TxnLike): Option[A] = {
      txe.withIO { (io, dbTxn) =>
        val out     = io.out
        val keyE    = io.keyE
        val valueE  = io.valueE

        out.reset()
        keyFun(out)
        val keySize = out.size
        val data = out.buffer
        keyE.setData(data, 0, keySize)
        if (db.get(dbTxn, keyE, valueE, LockMode.DEFAULT) == SUCCESS) {
          val in = DataInput(valueE.getData, valueE.getOffset, valueE.getSize)  // XXX TODO: could also recycle with queue
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
        val keySize = out.size
        val data    = out.buffer
        keyE.setData(data, 0, keySize)
        if (db.get(dbTxn, keyE, valueE, LockMode.DEFAULT) == SUCCESS) {
          val in = DataInput(valueE.getData, valueE.getOffset, valueE.getSize)
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
        val keySize   = out.size
        val data      = out.buffer
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
        val keySize   = out.size
        val data      = out.buffer
        keyE.setData(data, 0, keySize)
        db.delete(dbTxn, keyE) == SUCCESS
      }
    }

    def close(): Unit = db.close()

    def numEntries(implicit tx: TxnLike): Int = db.count().toInt
  }

  private final class TxEnv(val env: Environment, val txnCfg: TransactionConfig) extends ExternalDecider {
    @field private[this] val ioQueue   = new ConcurrentLinkedQueue[IO]
    @field private[this] val dbTxnRef  = TxnLocal(initialValue = { implicit tx =>
      ScalaTxn.setExternalDecider(this)
      val res = env.beginTransaction(null, txnCfg)
      val id  = res.getId
      log(s"txn begin  <$id>")
      ScalaTxn.afterRollback {
        case ScalaTxn.RolledBack(cause) =>
          log(s"txn rollback <$id>")
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
        log(s"txn commit <${dbTxn.getId}>")
        dbTxn.commit()
        true
      } catch {
        case NonFatal(e) =>
          e.printStackTrace()
          log(s"txn abort <${dbTxn.getId}>")
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
    @field val keyE      = new DatabaseEntry()
    @field val valueE    = new DatabaseEntry()
    @field val partialE  = new DatabaseEntry()
    @field val out       = DataOutput()

    partialE.setPartial(0, 0, true)
  }
}
trait BerkeleyDB extends DataStore
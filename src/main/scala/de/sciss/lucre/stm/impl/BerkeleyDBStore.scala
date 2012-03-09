/*
 *  BerkeleyDBStore.scala
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

import de.sciss.lucre.stm.TxnStore
import java.io.{File, FileNotFoundException}
import com.sleepycat.je.{Database, Environment, DatabaseConfig, TransactionConfig, EnvironmentConfig}

object BerkeleyDBStore {
   private val DB_CONSOLE_LOG_LEVEL = "OFF"   // XXX TODO

   def open[ Txn ]( file: File, createIfNecessary: Boolean = true ) : BerkeleyDBStore[ Txn ] = {
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
//         val kea     = Array[ Byte ]( 0, 0, 0, 0 )
//         val ke      = new DatabaseEntry( kea )  // slot for last-slot
//         val ve      = new DatabaseEntry()
//         val idCnt   = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
//            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
//            in.readInt()
//         } else 1
//         kea( 3 )    = 1.toByte   // slot for react-last-slot
//         val reactCnt = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
//            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
//            in.readInt()
//         } else 0
         txn.commit()
         new Impl[ Txn ]( env, db, txnCfg )
      } catch {
         case e =>
            txn.abort()
            throw e
      }
   }

   private final class Impl[ Txn ]( env: Environment, db: Database, txnCfg: TransactionConfig )
   extends BerkeleyDBStore[ Txn ] {
      private def todo() = sys.error( "TODO" )

      def put( key: Array[ Byte ])( writer: (DataOutput) => Unit )( implicit tx: Txn ) {
         todo()
      }

      def get[ A ]( key: Array[ Byte ])( reader: (DataInput) => A )( implicit tx: Txn ) = todo()

      def contains( key: Array[ Byte ])( implicit tx: Txn ) = todo()

      def remove( key: Array[ Byte ])( implicit tx: Txn ) { todo() }
   }
}
trait BerkeleyDBStore[ Txn ] extends TxnStore[ Txn ]
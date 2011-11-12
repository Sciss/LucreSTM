package de.sciss.lucrestm

import com.sleepycat.je.{EnvironmentConfig, TransactionConfig, DatabaseConfig, Environment}
import java.io.File


object Test {
   var DB_CONSOLE_LOG_LEVEL   = "OFF" // "ALL"
   val DB_NAME                = "skiplist"

   def run() {
      val envCfg  = new EnvironmentConfig()
      val txnCfg  = new TransactionConfig()
      val dbCfg   = new DatabaseConfig()

      envCfg.setTransactional( true )
      envCfg.setAllowCreate( true )
      dbCfg.setTransactional( true )
      dbCfg.setAllowCreate( true )

      val dir     = new File( "database" )
      dir.mkdirs()

//    envCfg.setConfigParam( EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL" )
      envCfg.setConfigParam( EnvironmentConfig.CONSOLE_LOGGING_LEVEL, DB_CONSOLE_LOG_LEVEL )
      val env     = new Environment( dir, envCfg )
      val stm     = LucreSTM.open( env, DB_NAME, dbCfg, txnCfg )
      // XXX
   }
}
package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => STMRef}
import java.util.concurrent.ConcurrentLinkedQueue
import concurrent.stm.{TxnLocal, Txn, InTxnEnd, TxnExecutor, InTxn, Ref => ScalaRef}
import com.sleepycat.je.{DatabaseConfig, EnvironmentConfig, TransactionConfig, Environment, Database, Transaction, OperationStatus, DatabaseEntry}
import java.io.{FileNotFoundException, File, IOException}

object BerkeleyDB {
   private val DB_CONSOLE_LOG_LEVEL   = "OFF" // "ALL"

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
      val db      = try {
         txn.setName( "Open '" + name + "'" )
         env.openDatabase( txn, name, dbCfg )
      } catch {
         case e =>
            txn.abort()
            throw e
      }
      txn.commit()
      new System( env, db, txnCfg )
   }

   private final class System( env: Environment, db: Database, txnCfg: TransactionConfig )
   extends BerkeleyDB with Txn.ExternalDecider {
      sys =>

//      private val peer        = new CCSTM()
      private val idCnt       = ScalaRef( 0 ) // peer.newRef( 0 )
      private val dbTxnSTMRef = TxnLocal( initialValue = initDBTxn( _ ))
      private val ioQueue     = new ConcurrentLinkedQueue[ IO ]

      def atomic[ Z ]( block: InTxn => Z ) : Z = TxnExecutor.defaultAtomic( block )

      def newRef[ A ]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : Ref[ A ] = {
         val res = new RefImpl[ A ]( newID, ser )
         res.set( init )
         res
      }

      def newRefArray[ A ]( size: Int ) : Array[ Ref[ A ]] = new Array[ Ref[ A ]]( size )

      def readRef[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Ref[ A ] = {
         val id = in.readInt()
         new RefImpl[ A ]( id, ser )
      }

      def writeRef[ A ]( ref: Ref[ A ], out: DataOutput ) {
         out.writeInt( ref.id )
      }

      def disposeRef[ A ]( ref: Ref[ A ])( implicit tx: InTxn ) {
         remove( ref.id )
      }

      def close() { db.close() }

      def numRefs : Long = db.count()

      private def txnHandle( implicit txn: InTxnEnd ) : Transaction = dbTxnSTMRef.get

      private def initDBTxn( implicit txn: InTxn ) : Transaction = {
         Txn.setExternalDecider( this )
         val dbTxn = env.beginTransaction( null, txnCfg )
         Txn.afterRollback { status =>
            try {
               dbTxn.abort()
            } catch {
               case _ =>
            }
         }
         dbTxn
      }

      private def newID( implicit tx: InTxn ) : Int = {
//      val id = idCnt.transformAndGet( _ + 1 )
         val id = idCnt.get + 1
         idCnt.set( id )
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

      def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: InTxn ) {
         withIO { io =>
            val out = io.beginWrite()
            valueFun( out )
            io.endWrite( id )
         }
      }

      def remove( id: Int )( implicit tx: InTxn ) {
         withIO( _.remove( id ))
      }

      def read[ A ]( id: Int )( valueFun: DataInput => A )( implicit tx: InTxn ) : A = {
         withIO { io =>
            val in = io.read( id )
            if( in != null ) {
               valueFun( in )
            } else {
//            Txn.retry
               throw new IOException()
            }
         }
      }

      private final class RefImpl[ A ]( val id: Int, ser: Serializer[ A ])
      extends Ref[ A ] {
         def set( v: A )( implicit txn: InTxn ) {
            sys.write( id )( ser.write( v, _ ))
         }

         def get( implicit txn: InTxn ) : A = {
            sys.read[ A ]( id )( ser.read( _ ))
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

         def read( key: Int )( implicit tx: InTxn ) : DataInput = {
            val h    = txnHandle
            val ve   = valueE
            if( db.get( h, keyE, ve, null ) == OperationStatus.SUCCESS ) {
               new DataInput( ve.getData, ve.getOffset, ve.getSize )
            } else {
               null
            }
         }

         def remove( key: Int )( implicit tx: InTxn ) {
            val h    = txnHandle
            val a    = keyArr
            a( 0 )   = (key >> 24).toByte
            a( 1 )   = (key >> 16).toByte
            a( 2 )   = (key >>  8).toByte
            a( 3 )   = key.toByte
            db.delete( h, keyE )
         }

         def endWrite( key: Int )( implicit tx: InTxn ) {
            val h    = txnHandle
            val a    = keyArr
            a( 0 )   = (key >> 24).toByte
            a( 1 )   = (key >> 16).toByte
            a( 2 )   = (key >>  8).toByte
            a( 3 )   = key.toByte
            out.flush()
            valueE.setData( out.toByteArray )
            db.put( h, keyE, valueE )
         }
      }

      def shouldCommit( implicit txn: InTxnEnd ) : Boolean = {
         val h = dbTxnSTMRef.get
         try {
            h.commit()
            true
         } catch {
            case e =>
               try {
                  h.abort()
               } catch {
                  case _ =>
               }
               false
         }
      }
   }

   sealed trait Ref[ A ] extends STMRef[ InTxn, A ] {
      private[BerkeleyDB] def id: Int
   }
}
sealed trait BerkeleyDB extends Sys[ BerkeleyDB ] {
   type Ref[ A ]  = BerkeleyDB.Ref[ A ]
   type Tx        = InTxn

   /**
    * Closes the underlying database. The STM cannot be used beyond this call.
    */
   def close() : Unit

   /**
    * Reports the current number of references stored in the database.
    */
   def numRefs : Long
}
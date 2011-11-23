package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref, Val => _Val}
import java.util.concurrent.ConcurrentLinkedQueue
import concurrent.stm.{TxnLocal, Txn, InTxnEnd, TxnExecutor, InTxn, Ref => ScalaRef}
import java.io.{FileNotFoundException, File, IOException}
import com.sleepycat.je.{DatabaseEntry, DatabaseConfig, EnvironmentConfig, TransactionConfig, Environment, Database, Transaction, OperationStatus}

object BerkeleyDB {
   /* private val */ var DB_CONSOLE_LOG_LEVEL   = "OFF" // "ALL"

   sealed trait ID extends Disposable[ InTxn ]

   private final class IDImpl( sys: System, id: Int ) extends ID {
      def dispose()( implicit tx: InTxn ) {
         sys.remove( id )
      }
   }

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

   private final class System( env: Environment, db: Database, txnCfg: TransactionConfig, idCnt: ScalaRef[ Int ])
   extends BerkeleyDB with Txn.ExternalDecider {
      system =>

//      private val peer        = new CCSTM()
//      private val idCnt       = ScalaRef( 0 ) // peer.newRef( 0 )
      private val dbTxnSTMRef = TxnLocal( initialValue = initDBTxn( _ ))
      private val ioQueue     = new ConcurrentLinkedQueue[ IO ]

      def root[ A ]( init: => A )( implicit tx: InTxn, ser: Serializer[ A ]) : A = {
         val rootID = 1
         tryRead[ A ]( rootID )( ser.read( _ )).getOrElse {
//println( "HERE CALLING NEWID" )
            val id   = newIDValue
//println( "DID CALL NEWID" )
            require( id == rootID, "Root can only be initialized on an empty database" )
//println( "CALLING INIT" )
            val res  = init
            write( id )( ser.write( res, _ ))
            res
         }
      }

      def atomic[ Z ]( block: InTxn => Z ) : Z = TxnExecutor.defaultAtomic( block )

      def newVal[ A ]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : Val[ A ] = {
         val res = new ValImpl[ A ]( newIDValue, ser )
         res.set( init )
         res
      }

//      def newRef[ A <: Disposable[ InTxn ]]()( implicit tx: InTxn, ser: Serializer[ A ]) : Ref[ A ] =
//         newRef[ A ]( EmptyMut )

      def newRef[ A <: Mutable[ BerkeleyDB, A ]]( init: A )( implicit tx: InTxn, reader: Reader[ A ]) : Ref[ A ] = {
         val res = new RefImpl[ A ]( newIDValue, reader )
         res.set( init )
         res
      }

//      def newMut[ A <: Disposable[ InTxn ]]( init: A )( implicit tx: InTxn, ser: Serializer[ A ]) : Mut[ A ] = {
//         val id   = newID
//         val res  = new MutImpl[ A ]( id, ser )
//         write( id )( ser.write( init, _ ))
//         res
//      }

      def newValArray[ A ]( size: Int ) : Array[ Val[ A ]] = new Array[ Val[ A ]]( size )

      def newRefArray[ A ]( size: Int ) : Array[ Ref[ A ]] = new Array[ Ref[ A ]]( size )

      def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Val[ A ] = {
         val id = in.readInt()
         new ValImpl[ A ]( id, ser )
      }

      def readRef[ A <: Mutable[ BerkeleyDB, A ]]( in: DataInput )( implicit reader: Reader[ A ]) : Ref[ A ] = {
         val id = in.readInt()
         new RefImpl[ A ]( id, reader )
      }

      def readMut[ A <: Mutable[ BerkeleyDB, A ]]( in: DataInput )( constr: ID => A ) : A = {
         val id = new IDImpl( this, in.readInt() )
//         if( id == -1 ) EmptyMut else new MutImpl[ A ]( id, ser )
         constr( id )
      }

//      def writeRef[ A ]( ref: Ref[ A ], out: DataOutput ) {
//         out.writeInt( ref.id )
//      }

//      def disposeRef[ A ]( ref: Ref[ A ])( implicit tx: InTxn ) {
//         remove( ref.id )
//      }

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

      private def newIDValue( implicit tx: InTxn ) : Int = {
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

      def newID( implicit tx: InTxn ) : ID = new IDImpl( this, newIDValue )

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

      def tryRead[ A ]( id: Int )( valueFun: DataInput => A )( implicit tx: InTxn ) : Option[ A ] = {
         withIO { io =>
            val in = io.read( id )
            if( in != null ) Some( valueFun( in )) else None
         }
      }

      private sealed trait SourceImpl[ A ] {
         protected def id: Int
         protected def reader: Reader[ A ]

         final def get( implicit tx: InTxn ) : A = {
//            peer.get( v )
            system.read[ A ]( id )( reader.read( _ ))
         }

         final def write( out: DataOutput ) {
            out.writeInt( id )
         }
      }

//      private final class MutSer[ A <: Disposable[ InTxn ]]( ser: Serializer[ A ]) extends Serializer[ Mut[ A ]] {
//         def read( in: DataInput ) : Mut[ A ] = system.readMut[ A ]( in )( ser )
//         def write( m: Mut[ A ], out: DataOutput ) { m.write( out )}
//      }

      private final class ValImpl[ A ]( /* peer: ScalaRef[ A ] */ protected val id: Int, protected val reader: Serializer[ A ])
      extends Val[ A ] with SourceImpl[ A ] {
         def set( v: A )( implicit tx: InTxn ) {
//            peer.set( v )
            system.write( id )( reader.write( v, _ ))
         }

         def transform( f: A => A )( implicit tx: InTxn ) { set( f( get ))}

         def dispose()( implicit tx: InTxn ) {
            system.remove( id )
         }

         def debug() {
            println( "Val(" + id + ")" )
         }
      }

      private final class RefImpl[ A <: Mutable[ BerkeleyDB, A ]]( protected val id: Int, protected val reader: Reader[ A ])
      extends Ref[ A ] with SourceImpl[ A ] /* with Serializer[ Mut[ A ]] */ {
//         protected def ser: Serializer[ Mut[ A ]] = this

         def debug() {
            println( "Ref(" + id + ")" )
         }

         def set( v: A )( implicit tx: InTxn ) {
            system.write( id ) { out =>
               /* if( v.isDefined ) */ v.write( out ) /* else out.writeInt( - 1 ) */
            }
         }

//         def getOrNull( implicit tx: InTxn ) : A = get.orNull

         def transform( f: A => A )( implicit tx: InTxn ) { set( f( get ))}

         def dispose()( implicit tx: InTxn ) {
            system.remove( id )
         }

//         def read( in: DataInput ) : Mut[ A ] = system.readMut[ A ]( in )( peerSer )
//         def write( m: Mut[ A ], out: DataOutput ) { m.write( out )}
      }

//      private case object EmptyMut extends Mut[ Nothing ] {
//         def isEmpty   = true
//         def isDefined = false
//         def get( implicit tx: InTxn ) : Nothing = sys.error( "Get on an empty mutable" )
//         def dispose()( implicit tx: InTxn ) {}
//         def write( out: DataOutput ) { out.writeInt( -1 )}
//         def orNull[ A1 >: Nothing ]( implicit tx: Tx /*, ev: <:<[ Null, A1 ]*/) : A1 = null.asInstanceOf[ A1 ]
//      }

//      private final class MutImpl[ A <: Disposable[ InTxn ]]( protected val id: Int, protected val ser: Reader[ A ])
//      extends Mut[ A ] with SourceImpl[ A ] {
//         def isEmpty   : Boolean = false
//         def isDefined : Boolean = true
//
//         def orNull[ A1 >: A ]( implicit tx: Tx /*, ev: <:<[ Null, A1 ]*/) : A1 = get
//
//         def dispose()( implicit tx: InTxn ) {
//            get.dispose()
//            system.remove( id )
//         }
//      }

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

         def read( key: Int )( implicit tx: InTxn ) : DataInput = {
            val h    = txnHandle
            keyToArray( key )
            val ve   = valueE
            if( db.get( h, keyE, ve, null ) == OperationStatus.SUCCESS ) {
               new DataInput( ve.getData, ve.getOffset, ve.getSize )
            } else {
               null
            }
         }

         def remove( key: Int )( implicit tx: InTxn ) {
            val h    = txnHandle
            keyToArray( key )
            db.delete( h, keyE )
         }

         def endWrite( key: Int )( implicit tx: InTxn ) {
            val h    = txnHandle
            keyToArray( key )
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

   sealed trait Ref[ A ] extends _Ref[ InTxn, /* Mut, */ A ] {
//      private[BerkeleyDB] def id: Int
//      protected def id: Int
//      def debug() {
//         println( "ID = " + id )
//      }
   }

//   sealed trait Mut[ +A ] extends Mutable[ InTxn, A ]
   sealed trait Val[ A ] extends _Val[ InTxn, A ]
}
sealed trait BerkeleyDB extends Sys[ BerkeleyDB ] {
   type Val[ A ]  = BerkeleyDB.Val[ A ]
   type Ref[ A ]  = BerkeleyDB.Ref[ A ]
//   type Mut[ +A ] = BerkeleyDB.Mut[ A ]
   type ID        = BerkeleyDB.ID
   type Tx        = InTxn

   /**
    * Closes the underlying database. The STM cannot be used beyond this call.
    */
   def close() : Unit

   /**
    * Reports the current number of references stored in the database.
    */
   def numRefs : Long

   /**
    * Reads the root object representing the stored datastructure,
    * or provides a newly initialized one via the `init` argument,
    * if no root has been stored yet.
    */
   def root[ A ]( init: => A )( implicit tx: Tx, ser: Serializer[ A ]) : A
}
package de.sciss.lucrestm

import concurrent.stm.impl.STMImpl
import concurrent.stm.ccstm.CCSTM
import actors.threadpool.TimeUnit
import concurrent.stm.Txn.Status
import collection.mutable.Builder
import concurrent.stm.{Txn, CommitBarrier, TxnExecutor, TxnLocal, TMap, TSet, MaybeTxn, TArray, InTxnEnd, InTxn, Ref => STMRef}
import com.sleepycat.bind.tuple.{TupleInput, TupleOutput}
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.{IOException, ObjectOutputStream, ObjectInputStream}
import com.sleepycat.je.{DatabaseConfig, OperationStatus, DatabaseEntry, Database, Environment, TransactionConfig, Transaction}

object LucreSTM {
   def open( env: Environment, name: String, dbCfg: DatabaseConfig, txnCfg: TransactionConfig ) : LucreSTM = {
      sys.error( "TODO" )
//      val envCfg  = env.getConfig
//      require( envCfg.getTransactional && dbCfg.getTransactional && !dbCfg.getSortedDuplicates )
//
//      val txn  = env.beginTransaction( null, txnCfg )
//      var ok   = false
//      try {
//         txn.setName( "Open '" + name + "'" )
//         val db = env.openDatabase( txn, name, dbCfg )
//         try {
//            val res = fun( ctx, db )
//            ok = true
//            res
//         } finally {
//            if( !ok ) db.close()
//         }
//      } finally {
//         if( ok ) txn.commit() else txn.abort()
//      }
   }
}
final class LucreSTM private ( env: Environment, txnCfg: TransactionConfig, db: Database )
extends STMImpl {
   private val peer     = new CCSTM()

   private val idCnt    = peer.newRef( 0 )

   private val dbTxnSTMRef = TxnLocal( initialValue = initDBTxn( _ ))

   private val ioQueue  = new ConcurrentLinkedQueue[ IO ]

   private def txnHandle( implicit txn: InTxnEnd ) : Transaction = dbTxnSTMRef.get

   private def initDBTxn( implicit txn: InTxn ) : Transaction = {
      Txn.setExternalDecider( Decider )
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

   private[lucrestm] def newID( implicit tx: InTxn ) : Int = {
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

   private[lucrestm] def write( id: Int )( valueFun: ObjectOutputStream => Unit )( implicit tx: InTxn ) {
      withIO { io =>
         val out = io.beginWrite()
         valueFun( out )
         io.endWrite( id )
      }
   }

   private[lucrestm] def remove( id: Int )( implicit tx: InTxn ) {
      withIO( _.remove( id ))
   }

   private[lucrestm] def read[ A ]( id: Int )( valueFun: ObjectInputStream => A )( implicit tx: InTxn ) : A = {
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

   private final class IO {
      private val keyArr   = new Array[ Byte ]( 4 )
      private val keyE     = new DatabaseEntry( keyArr )
      private val valueE   = new DatabaseEntry()
//      private val ti       = new TupleInput()
      private val to       = new TupleOutput()
//      private val is       = new ObjectInputStream( ti )
      private val os       = new ObjectOutputStream( to )

//      def beginRead() : ObjectInputStream = {
//         ti.reset()
//         is
//      }

      def beginWrite() : ObjectOutputStream = {
         to.reset()
         os
      }

      def read( key: Int )( implicit tx: InTxn ) : ObjectInputStream = {
         val h    = txnHandle
         val ve   = valueE
         if( db.get( h, keyE, ve, null ) == OperationStatus.SUCCESS ) {
            val ti = new TupleInput( ve.getData, ve.getOffset, ve.getSize )
            new ObjectInputStream( ti )
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
         os.flush()
         valueE.setData( to.toByteArray )
         db.put( h, keyE, valueE )
      }
   }

   private object Decider extends Txn.ExternalDecider {
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

//   private[lucrestm] def newID() : Array[ Byte ] = {
//      val id   = idCnt.single.transformAndGet( _ + 1 )
//      val arr  = new Array[ Byte ]( 4 )
//      arr( 0 ) = (id >> 24).toByte
//      arr( 1 ) = (id >> 16).toByte
//      arr( 2 ) = (id >>  8).toByte
//      arr( 3 ) = id.toByte
//      arr
//   }

   private def notYetImplemented : Nothing = sys.error( "Not yet implemented" )

   def newRef( v0: Boolean ) : STMRef[ Boolean ]   = LucreRef[ Boolean ]( this, v0 )
   def newRef( v0: Byte    ) : STMRef[ Byte    ]   = LucreRef[ Byte    ]( this, v0 )
   def newRef( v0: Short   ) : STMRef[ Short   ]   = LucreRef[ Short   ]( this, v0 )
   def newRef( v0: Char    ) : STMRef[ Char    ]   = LucreRef[ Char    ]( this, v0 )
   def newRef( v0: Int     ) : STMRef[ Int     ]   = LucreRef[ Int     ]( this, v0 )
   def newRef( v0: Float   ) : STMRef[ Float   ]   = LucreRef[ Float   ]( this, v0 )
   def newRef( v0: Long    ) : STMRef[ Long    ]   = LucreRef[ Long    ]( this, v0 )
   def newRef( v0: Double  ) : STMRef[ Double  ]   = LucreRef[ Double  ]( this, v0 )
   def newRef( v0: Unit    ) : STMRef[ Unit    ]   = LucreRef[ Unit    ]( this, v0 )
   def newRef[ A ]( v0: A )( implicit mf: ClassManifest[ A ]) : STMRef[ A ] = LucreRef[ A ]( this, v0 )

   def newTxnLocal[ A ]( init: => A, initialValue: (InTxn) => A, beforeCommit: (InTxn) => Unit,
                         whilePreparing: (InTxnEnd) => Unit, whileCommitting: (InTxnEnd) => Unit,
                         afterCommit: (A) => Unit, afterRollback: (Status) => Unit,
                         afterCompletion: (Status) => Unit) : TxnLocal[ A ] = notYetImplemented

   def newTArray[ A ]( length: Int )( implicit mf: ClassManifest[ A ]) : TArray[ A ]               = notYetImplemented
   def newTArray[ A ]( xs: TraversableOnce[ A ])( implicit mf: ClassManifest[ A ]) : TArray[ A ]   = notYetImplemented

   def newTMap[ A, B ] : TMap[ A, B ] = notYetImplemented
   def newTMapBuilder[ A, B ] : Builder[ (A, B), TMap[ A, B ]] = notYetImplemented
   def newTSet[ A ] : TSet[ A ] = notYetImplemented
   def newTSetBuilder[ A ] : Builder[ A, TSet[ A ]] = notYetImplemented

   // ---- proxy for the following ----

   def apply[ Z ]( block: (InTxn) => Z )( implicit mt: MaybeTxn ) : Z = peer.apply[ Z ]( block )( mt )
   def oneOf[ Z ]( blocks: Function1[ InTxn, Z ]* )( implicit mt: MaybeTxn ) : Z = peer.oneOf[ Z ]( blocks: _* )( mt )

   def pushAlternative[ Z ]( mt: MaybeTxn, block: (InTxn) => Z ) : Boolean = peer.pushAlternative[ Z ]( mt, block )

   def compareAndSet[ A, B ]( a: STMRef[ A ], a0: A, a1: A, b: STMRef[ B ], b0: B, b1: B ) : Boolean =
      peer.compareAndSet[ A, B ]( a, a0, a1, b, b0, b1 )

   def compareAndSetIdentity[ A <: AnyRef, B <: AnyRef ]( a: STMRef[ A ], a0: A, a1: A, b: STMRef[ B ], b0: B, b1: B ) : Boolean =
      peer.compareAndSetIdentity[ A, B ]( a, a0, a1, b, b0, b1 )

   def retryTimeoutNanos : Option[ Long ] = peer.retryTimeoutNanos

   def withRetryTimeoutNanos( timeoutNanos: Option[ Long ]) : TxnExecutor = peer.withRetryTimeoutNanos( timeoutNanos )

   def isControlFlow( x: Throwable ) : Boolean = peer.isControlFlow( x )

   def withControlFlowRecognizer( pf: PartialFunction[ Throwable, Boolean ]) : TxnExecutor =
      peer.withControlFlowRecognizer( pf )

   def postDecisionFailureHandler : (Status, Throwable) => Unit = peer.postDecisionFailureHandler

   def withPostDecisionFailureHandler( handler: (Status, Throwable) => Unit ) : TxnExecutor =
      peer.withPostDecisionFailureHandler( handler )

   def newCommitBarrier( timeout: Long, unit: TimeUnit ) : CommitBarrier = peer.newCommitBarrier( timeout, unit )

   def findCurrent( implicit mt: MaybeTxn ) : Option[ InTxn ] = peer.findCurrent( mt )

   def dynCurrentOrNull : InTxn = peer.dynCurrentOrNull
}
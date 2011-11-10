package de.sciss.lucrestm

import concurrent.stm.impl.STMImpl
import concurrent.stm.ccstm.CCSTM
import actors.threadpool.TimeUnit
import concurrent.stm.Txn.Status
import collection.mutable.Builder
import concurrent.stm.{Txn, CommitBarrier, TxnExecutor, TxnLocal, TMap, TSet, MaybeTxn, TArray, InTxnEnd, InTxn, Ref}
import com.sleepycat.je.{Database, Environment, TransactionConfig, Transaction}

final class LucreSTM( env: Environment, txnCfg: TransactionConfig, private[lucrestm] val db: Database )
extends STMImpl {
   private val peer = new CCSTM()

   private val idCnt = peer.newRef( 0 )

   private val dbTxnRef = TxnLocal( initialValue = initDBTxn( _ ))

   private[lucrestm] def txnHandle( implicit txn: InTxnEnd ) : Transaction = dbTxnRef.get

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

   private object Decider extends Txn.ExternalDecider {
      def shouldCommit( implicit txn: InTxnEnd ) : Boolean = {
         val h = dbTxnRef.get
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

   private[lucrestm] def newID() : Array[ Byte ] = {
      val id   = idCnt.single.transformAndGet( _ + 1 )
      val arr  = new Array[ Byte ]( 4 )
      arr( 0 ) = (id >> 24).toByte
      arr( 1 ) = (id >> 16).toByte
      arr( 2 ) = (id >>  8).toByte
      arr( 3 ) = id.toByte
      arr
   }

   private def notYetImplemented : Nothing = sys.error( "Not yet implemented" )

   def newRef( v0: Boolean ) : Ref[ Boolean ]   = notYetImplemented
   def newRef( v0: Byte ) : Ref[ Byte ]         = notYetImplemented
   def newRef( v0: Short ) : Ref[ Short ]       = notYetImplemented
   def newRef( v0: Char ) : Ref[ Char ]         = notYetImplemented
   def newRef( v0: Int ) : Ref[ Int ]           = notYetImplemented
   def newRef( v0: Float ) : Ref[ Float ]       = notYetImplemented
   def newRef( v0: Long ) : Ref[ Long ]         = notYetImplemented
   def newRef( v0: Double ) : Ref[ Double ]     = notYetImplemented
   def newRef( v0: Unit ) : Ref[ Unit ]         = notYetImplemented
   def newRef[ A ]( v0: A )( implicit mf: ClassManifest[ A ]) : Ref[ A ] = notYetImplemented

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

   def compareAndSet[ A, B ]( a: Ref[ A ], a0: A, a1: A, b: Ref[ B ], b0: B, b1: B ) : Boolean =
      peer.compareAndSet[ A, B ]( a, a0, a1, b, b0, b1 )

   def compareAndSetIdentity[ A <: AnyRef, B <: AnyRef ]( a: Ref[ A ], a0: A, a1: A, b: Ref[ B ], b0: B, b1: B ) : Boolean =
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
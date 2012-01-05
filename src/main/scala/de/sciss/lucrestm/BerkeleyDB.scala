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

package de.sciss.lucrestm

import de.sciss.lucrestm.{Var => _Var, Txn => _Txn}
import java.util.concurrent.ConcurrentLinkedQueue
import concurrent.stm.{Txn => ScalaTxn, InTxnEnd, TxnExecutor, InTxn, Ref => ScalaRef}
import java.io.{FileNotFoundException, File, IOException}
import com.sleepycat.je.{DatabaseEntry, DatabaseConfig, EnvironmentConfig, TransactionConfig, Environment, Database, Transaction, OperationStatus}
import annotation.elidable
import elidable.CONFIG
import collection.immutable.{IndexedSeq => IIdxSeq}

object BerkeleyDB {
   import LucreSTM.logConfig

   private type S = BerkeleyDB

   /* private val */ var DB_CONSOLE_LOG_LEVEL   = "OFF" // "ALL"

   sealed trait ID extends Identifier[ Txn ]

   def open( file: File, createIfNecessary: Boolean = true ) : S = {
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
         val kea     = Array[ Byte ]( 0, 0, 0, 0 )
         val ke      = new DatabaseEntry( kea )  // key for last-key
         val ve      = new DatabaseEntry()
         val idCnt   = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
            in.readInt()
         } else 1
         kea( 3 )    = 1.toByte   // key for react-last-key
         val reactCnt = if( db.get( txn, ke, ve, null ) == OperationStatus.SUCCESS ) {
            val in   = new DataInput( ve.getData, ve.getOffset, ve.getSize )
            in.readInt()
         } else 0
         txn.commit()
         new System( env, db, txnCfg, ScalaRef( idCnt ), ScalaRef( reactCnt ))
      } catch {
         case e =>
            txn.abort()
            throw e
      }
   }

   private final class System( val env: Environment, db: Database, val txnCfg: TransactionConfig,
                               idCnt: ScalaRef[ Int ], reactCnt: ScalaRef[ Int ])
   extends BerkeleyDB /* with ScalaTxn.ExternalDecider */ {
      system =>

      def manifest: Manifest[ S ] = Manifest.classType( classOf[ BerkeleyDB ])

      private val ioQueue     = new ConcurrentLinkedQueue[ IO ]
      private val idCntVar    = new CachedIntVar( 0, idCnt )
      private val reactCntVar = new CachedIntVar( 1, idCnt )
      private val inMem       = InMemory()

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, InMemory ]( inMem.atomic { implicit tx =>
         tx.newIntVar( tx.newID(), 0 )
      })( ctx => inMem.wrap( ctx.peer ))

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

      def newIDValue()( implicit tx: Txn ) : Int = {
         val id = idCntVar.get( tx ) + 1
         logConfig( "new " + id )
         idCntVar.set( id )
         id
      }

      def newReactValue()( implicit tx: Txn ) : Int = {
         val id = reactCntVar.get( tx ) + 1
         logConfig( "new react " + id )
         reactCntVar.set( id )
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

      /* final */ def dispose()( implicit tx: Txn ) {
         tx.system.remove( id )
      }

      @elidable(CONFIG) protected final def assertExists()( implicit tx: Txn ) {
         require( tx.system.exists( id ), "trying to write disposed ref " + id )
      }
   }

//   private type Obs[ A ]    = Observer[ Txn, Change[ A ]]
//   private type ObsVar[ A ] = Var[ A ] with State[ S, Change[ A ]]

   private sealed trait BasicVar[ A ] extends Var[ A ] with BasicSource {
      protected def ser: TxnSerializer[ Txn, Unit, A ]

      def get( implicit tx: Txn ) : A = {
         tx.system.read[ A ]( id )( ser.read( _, () ))
      }

      def setInit( v: A )( implicit tx: Txn ) {
         tx.system.write( id )( ser.write( v, _ ))
      }
   }

   private final class VarImpl[ A ]( protected val id: Int, protected val ser: TxnSerializer[ Txn, Unit, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( ser.write( v, _ ))
      }

      def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var(" + id + ")"
   }

//   private sealed trait BasicObservable[ @specialized A ] extends BasicSource with State[ S, Change[ A ]] {
//      def addObserver( observer: Obs[ A ])( implicit tx: Txn ) {
//         sys.error( "TODO" )
//      }
//
//      def removeObserver( observer: Obs[ A ])( implicit tx: Txn ) {
//         sys.error( "TODO" )
//      }
//
//      protected def notifyObservers( change: Change[ A ])( implicit tx: Txn ) {
//         val system  = tx.system
//         val oid     = 0x80000000 | id
//         if( system.exists( oid )) {
//            system.read[ Unit ]( oid ) { in =>
//               val sz = in.readInt()
//               var i = 0; while( i < sz ) {
//                  sys.error( "TODO" )
//               i += 1 }
//            }
//         }
//      }
//
//      final override def dispose()( implicit tx: Txn ) {
//         super.dispose()
//         tx.system.remove( 0x80000000 | id )
//      }
//   }

//   private final class ObsVarImpl[ A ]( protected val id: Int, protected val ser: TxnSerializer[ Txn, Unit, A ])
//   extends BasicVar[ A ] with BasicObservable[ A ] {
//      def set( now: A )( implicit tx: Txn ) {
//         val before = get( tx )
//         if( before != now ) {
//            tx.system.write( id )( ser.write( now, _ ))
//            notifyObservers( new Change( before, now ))
//         }
//      }
//
//      def transform( f: A => A )( implicit tx: Txn ) {
//         val before  = get( tx )
//         val now     = f( before )
//         if( before != now ) {
//            tx.system.write( id )( ser.write( now, _ ))
//            notifyObservers( new Change( before, now ))
//         }
//      }
//
//      override def toString = "ObsVar(" + id + ")"
//   }

//   private final class ObsIntVar( protected val id: Int )
//   extends BasicIntVar with BasicObservable[ Int ] {
//      def set( now: Int )( implicit tx: Txn ) {
//         val before = get( tx )
//         if( before != now ) {
//            tx.system.write( id )( _.writeInt( now ))
//            notifyObservers( new Change( before, now ))
//         }
//      }
//
//      def transform( f: Int => Int )( implicit tx: Txn ) {
//         val before  = get( tx )
//         val now     = f( before )
//         if( before != now ) {
//            tx.system.write( id )( _.writeInt( now ))
//            notifyObservers( new Change( before, now ))
//         }
//      }
//
//      override def toString = "ObsVar[Int](" + id + ")"
//   }

   private final class IntVar( protected val id: Int )
   extends Var[ Int ] with BasicSource {
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

      override def toString = "Var[Int](" + id + ")"
   }

   private final class CachedIntVar( protected val id: Int, peer: ScalaRef[ Int ])
   extends Var[ Int ] with BasicSource {
      def get( implicit tx: Txn ) : Int = peer.get( tx.peer )

      def setInit( v: Int )( implicit tx: Txn ) { set( v )}

      def set( v: Int )( implicit tx: Txn ) {
         peer.set( v )( tx.peer )
         tx.system.write( id )( _.writeInt( v ))
      }

      def transform( f: Int => Int )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var[Int](" + id + ")"
   }

   private final class LongVar( protected val id: Int )
   extends Var[ Long ] with BasicSource {
      def get( implicit tx: Txn ) : Long = {
         tx.system.read[ Long ]( id )( _.readLong() )
      }

      def setInit( v: Long )( implicit tx: Txn ) {
         tx.system.write( id )( _.writeLong( v ))
      }

      def set( v: Long )( implicit tx: Txn ) {
         assertExists()
         tx.system.write( id )( _.writeLong( v ))
      }

      def transform( f: Long => Long )( implicit tx: Txn ) { set( f( get ))}

      override def toString = "Var[Long](" + id + ")"
   }

//   private final class CachedLongVar( protected val id: Int, peer: ScalaRef[ Long ])
//   extends Var[ Long ] with BasicSource {
//      def get( implicit tx: Txn ) : Long = peer.get( tx.peer )
//
//      def setInit( v: Long )( implicit tx: Txn ) { set( v )}
//
//      def set( v: Long )( implicit tx: Txn ) {
//         tx.system.write( id )( _.writeLong( v ))
//      }
//
//      def transform( f: Long => Long )( implicit tx: Txn ) { set( f( get ))}
//
//      override def toString = "Var[Long](" + id + ")"
//   }

   sealed trait Var[ @specialized A ] extends _Var[ Txn, A ]

   sealed trait Txn extends _Txn[ S ] {
      private[BerkeleyDB] def dbTxn: Transaction
   }

   private final class TxnImpl( val system: System, val peer: InTxn )
   extends Txn with ScalaTxn.ExternalDecider {
      private var id = -1L

      def newID() : ID = new IDImpl( system.newIDValue()( this ))

//      def addStateReaction( fun: Txn => Unit ) : StateReactorLeaf[ S ] = system.reactionMap.addState( fun )( this )

      def addStateReaction[ A, Repr <: State[ S, A /*, Repr */]](
         /* source: Repr, */ reader: State.Reader[ S, Repr ], fun: (Txn, A) => Unit ) : State.ReactorKey[ S ] =
            system.reactionMap.addStateReaction( /* source, */ reader, fun )( this )

      def mapStateTargets( in: DataInput, access: S#Acc, targets: State.Targets[ S ],
                                               keys: IIdxSeq[ Int ]) : State.Reactor[ S ] =
         system.reactionMap.mapStateTargets( in, access, targets, keys )( this )

      def propagateState( key: Int, state: State[ S, _ /*, _ */],
                                            reactions: State.Reactions ) : State.Reactions =
         system.reactionMap.propagateState( key, state, reactions )( this )

      def removeStateReaction( key: State.ReactorKey[ S ]) { system.reactionMap.removeStateReaction( key )( this )}

//      def addState[ A ]( reader: A, fun: (Txn, A) => Unit )( implicit tx: Txn ) : StateReactorLeaf[ S ] =
//         system.reactionMap.addState( reader, fun )

//      private[lucrestm] def removeStateReaction( leaf: StateReactorLeaf[ S ]) { system.reactionMap.removeState( leaf )( this )}
//      private[lucrestm] def invokeStateReaction( leaf: StateReactorLeaf[ S ]) { system.reactionMap.invokeState( leaf )( this )}

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

      def newVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         val res = new VarImpl[ A ]( system.newIDValue()( this ), ser )
         res.setInit( init )( this )
         res
      }

      def newIntVar( id: ID, init: Int ) : Var[ Int ] = {
         val res = new IntVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newLongVar( id: ID, init: Long ) : Var[ Long ] = {
         val res = new LongVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

//      def newObservableVar[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : ObsVar[ A ] = {
//         val res = new ObsVarImpl[ A ]( system.newIDValue()( this ), ser )
//         res.setInit( init )( this )
//         res
//      }
//
//      def newObservableIntVar( id: ID, init: Int ) : ObsVar[ Int ] = {
//         val res = new ObsIntVar( system.newIDValue()( this ) )
//         res.setInit( init )( this )
//         res
//      }

      def newVarArray[ A ]( size: Int ) : Array[ Var[ A ]] = new Array[ Var[ A ]]( size )

      def readVar[ A ]( pid: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, Unit, A ]) : Var[ A ] = {
         val id = in.readInt()
         new VarImpl[ A ]( id, ser )
      }

      def readIntVar( pid: ID, in: DataInput ) : Var[ Int ] = {
         val id = in.readInt()
         new IntVar( id )
      }

      def readLongVar( pid: ID, in: DataInput ) : Var[ Long ] = {
         val id = in.readInt()
         new LongVar( id )
      }

      def readID( in: DataInput, acc: Unit ) : ID = new IDImpl( in.readInt() )

//      def readMut[ A <: Mutable[ S ]]( pid: ID, in: DataInput )
//                                              ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
//         val id = new IDImpl( in.readInt() )
//         reader.readData( in, id )( this )
//      }
//
//      def readOptionMut[ A <: MutableOption[ S ]]( pid: ID, in: DataInput )
//                                                          ( implicit reader: MutableOptionReader[ ID, Txn, A ]) : A = {
//         val mid = in.readInt()
//         if( mid == -1 ) reader.empty else {
//            reader.readData( in, new IDImpl( mid ))( this )
//         }
//      }

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
   type Var[ @specialized A ] = BerkeleyDB.Var[ A ]
   type ID                    = BerkeleyDB.ID
   type Tx                    = BerkeleyDB.Txn
   type Acc                   = Unit

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
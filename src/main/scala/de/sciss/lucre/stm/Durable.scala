/*
 *  Durable.scala
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

import stm.{Var => _Var, Txn => _Txn}
import concurrent.stm.{TxnExecutor, InTxn, Ref => ScalaRef}
import annotation.elidable
import elidable.CONFIG
import event.ReactionMap
import LucreSTM.logSTM

object Durable {
   private type S = Durable

   sealed trait ID extends Identifier[ S#Tx ] {
      private[ Durable ] def id: Int
   }

   def apply( store: DataStore ) : S = new System( store )

   def apply( factory: DataStoreFactory[ DataStore ], name: String = "data" ) : S =
      apply( factory.open( name ))

//   private object IDOrdering extends Ordering[ S#ID ] {
//      def compare( a: S#ID, b: S#ID ) : Int = {
//         val aid = a.id
//         val bid = b.id
//         if( aid < bid ) -1 else if( aid > bid ) 1 else 0
//      }
//   }

   private final class IDImpl( val id: Int ) extends ID {
      def write( out: DataOutput ) {
         out.writeInt( id )
      }

      override def hashCode: Int = id

      override def equals( that: Any ) : Boolean = {
         that.isInstanceOf[ IDImpl ] && (id == that.asInstanceOf[ IDImpl ].id)
      }

      def dispose()( implicit tx: S#Tx ) {
         tx.system.remove( id )
      }

      override def toString = "<" + id + ">"
   }

   private final class IDMapImpl[ A ]( mapID: Int )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ])
   extends IdentifierMap[ S#Tx, S#ID, A ] {
      private val idn = mapID.toLong << 32

      def get( id: S#ID )( implicit tx: S#Tx ) : Option[ A ] = {
         tx.system.tryRead( idn | (id.id.toLong & 0xFFFFFFFFL) )( serializer.read( _, () ))
      }

      def getOrElse( id: S#ID, default: => A )( implicit tx: S#Tx ) : A = get( id ).getOrElse( default )

      def put( id: S#ID, value: A )( implicit tx: S#Tx ) {
         tx.system.write( idn | (id.id.toLong & 0xFFFFFFFFL) )( serializer.write( value, _ ))
      }
      def contains( id: S#ID )( implicit tx: S#Tx ) : Boolean = {
         tx.system.exists( idn | (id.id.toLong & 0xFFFFFFFFL) )
      }
      def remove( id: S#ID )( implicit tx: S#Tx ) {
         tx.system.remove( idn | (id.id.toLong & 0xFFFFFFFFL) )
      }

      override def toString = "IdentifierMap<" + mapID + ">"
   }

   private sealed trait BasicSource {
      protected def id: Int

      final def write( out: DataOutput ) {
         out.writeInt( id )
      }

      /* final */
      def dispose()( implicit tx: S#Tx ) {
         tx.system.remove( id )
      }

      @elidable(CONFIG) protected final def assertExists()( implicit tx: S#Tx ) {
         require( tx.system.exists( id ), "trying to write disposed ref " + id )
      }

      final def isFresh( implicit tx: S#Tx ) : Boolean = true
   }

   private sealed trait BasicVar[ A ] extends Var[ A ] with BasicSource {
      protected def ser: TxnSerializer[ S#Tx, S#Acc, A ]

      final def get( implicit tx: S#Tx ) : A = tx.system.read[ A ]( id )( ser.read( _, () ))

//      final def getFresh( implicit tx: S#Tx ) : A = get

      final def setInit( v: A )( implicit tx: S#Tx ) { tx.system.write( id )( ser.write( v, _ ))}
   }

   private final class VarImpl[ A ]( protected val id: Int, protected val ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends BasicVar[ A ] {
      def set( v: A )( implicit tx: S#Tx ) {
         assertExists()
         tx.system.write( id )( ser.write( v, _ ))
      }

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var(" + id + ")"
   }

   private final class CachedVarImpl[ A ]( protected val id: Int, peer: ScalaRef[ A ],
                                           ser: TxnSerializer[ S#Tx, S#Acc, A ])
   extends Var[ A ] with BasicSource {
      def get( implicit tx: S#Tx ) : A = peer.get( tx.peer )

//      def getFresh( implicit tx: S#Tx ) : A = get

      def setInit( v: A )( implicit tx: S#Tx ) { set( v )}

      def set( v: A )( implicit tx: S#Tx ) {
         peer.set( v )( tx.peer )
         tx.system.write( id )( ser.write( v, _ ))
      }

      def writeInit()( implicit tx: S#Tx ) {
         tx.system.write( id )( ser.write( get, _ ))
      }

      def readInit()( implicit tx: S#Tx ) {
         peer.set( tx.system.read( id )( ser.read( _, () )))( tx.peer )
      }

      def transform( f: A => A )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var(" + id + ")"
   }

   private final class BooleanVar( protected val id: Int )
   extends Var[ Boolean ] with BasicSource {
      def get( implicit tx: S#Tx ): Boolean = {
         tx.system.read[ Boolean ]( id )( _.readBoolean() )
      }

//      def getFresh( implicit tx: S#Tx ) : Boolean = get

      def setInit( v: Boolean )( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeBoolean( v ))
      }

      def set( v: Boolean )( implicit tx: S#Tx ) {
         assertExists()
         tx.system.write( id )( _.writeBoolean( v ))
      }

      def transform( f: Boolean => Boolean )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Boolean](" + id + ")"
   }

   private final class IntVar( protected val id: Int )
   extends Var[ Int ] with BasicSource {
      def get( implicit tx: S#Tx ) : Int = {
         tx.system.read[ Int ]( id )( _.readInt() )
      }

//      def getFresh( implicit tx: S#Tx ) : Int = get

      def setInit( v: Int )( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeInt( v ))
      }

      def set( v: Int )( implicit tx: S#Tx ) {
         assertExists()
         tx.system.write( id )( _.writeInt( v ))
      }

      def transform( f: Int => Int )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Int](" + id + ")"
   }

   private final class CachedIntVar( protected val id: Int, peer: ScalaRef[ Int ])
   extends Var[ Int ] with BasicSource {
      def get( implicit tx: S#Tx ) : Int = peer.get( tx.peer )

//      def getFresh( implicit tx: S#Tx ) : Int = get

      def setInit( v: Int )( implicit tx: S#Tx ) { set( v )}

      def set( v: Int )( implicit tx: S#Tx ) {
         peer.set( v )( tx.peer )
         tx.system.write( id )( _.writeInt( v ))
      }

      def writeInit()( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeInt( get ))
      }

      def readInit()( implicit tx: S#Tx ) {
         peer.set( tx.system.read( id )( _.readInt() ))( tx.peer )
      }

      def transform( f: Int => Int )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Int](" + id + ")"
   }

   private final class LongVar( protected val id: Int )
   extends Var[ Long ] with BasicSource {
      def get( implicit tx: S#Tx ) : Long = {
         tx.system.read[ Long ]( id )( _.readLong() )
      }

//      def getFresh( implicit tx: S#Tx ) : Long = get

      def setInit( v: Long )( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeLong( v ))
      }

      def set( v: Long )( implicit tx: S#Tx ) {
         assertExists()
         tx.system.write( id )( _.writeLong( v ))
      }

      def transform( f: Long => Long )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Long](" + id + ")"
   }

   private final class CachedLongVar( protected val id: Int, peer: ScalaRef[ Long ])
   extends Var[ Long ] with BasicSource {
      def get( implicit tx: S#Tx ) : Long = peer.get( tx.peer )

//      def getFresh( implicit tx: S#Tx ) : Long = get

      def setInit( v: Long )( implicit tx: S#Tx ) { set( v )}

      def set( v: Long )( implicit tx: S#Tx ) {
         peer.set( v )( tx.peer )
         tx.system.write( id )( _.writeLong( v ))
      }

      def writeInit()( implicit tx: S#Tx ) {
         tx.system.write( id )( _.writeLong( get ))
      }

      def readInit()( implicit tx: S#Tx ) {
         peer.set( tx.system.read( id )( _.readLong() ))( tx.peer )
      }

      def transform( f: Long => Long )( implicit tx: S#Tx ) { set( f( get ))}

      override def toString = "Var[Long](" + id + ")"
   }

   sealed trait Var[ @specialized A ] extends _Var[ S#Tx, A ]

   sealed trait Txn extends _Txn[ S ] {
      def newCachedVar[ A ]( init: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
      def newCachedIntVar( init: Int ) : S#Var[ Int ]
      def newCachedLongVar( init: Long ) : S#Var[ Long ]
      def readCachedVar[ A ]( in: DataInput )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ]
      def readCachedIntVar( in: DataInput ) : S#Var[ Int ]
      def readCachedLongVar( in: DataInput ) : S#Var[ Long ]
   }

   private final class TxnImpl( val system: System, val peer: InTxn )
   extends Txn {
      //      private var id = -1L

      def newID(): S#ID = new IDImpl( system.newIDValue()( this ))
      def newPartialID(): S#ID = newID()

      def reactionMap: ReactionMap[ S ] = system.reactionMap

      override def toString = "Txn" // <" + id + ">"

      def newVar[ A ]( id: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]): S#Var[ A ] = {
         val res = new VarImpl[ A ]( system.newIDValue()( this ), ser )
         res.setInit( init )( this )
         res
      }

      def newPartialVar[ A ]( id: S#ID, init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]): S#Var[ A ] =
         newVar( id, init )

      def newCachedVar[ A ]( init: A )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]): S#Var[ A ] = {
         val res = new CachedVarImpl[ A ]( system.newIDValue()( this ), ScalaRef( init ), ser )
         res.writeInit()( this )
         res
      }

      def newBooleanVar( id: S#ID, init: Boolean ): S#Var[ Boolean ] = {
         val res = new BooleanVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newIntVar( id: S#ID, init: Int ): S#Var[ Int ] = {
         val res = new IntVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newCachedIntVar( init: Int ): S#Var[ Int ] = {
         val res = new CachedIntVar( system.newIDValue()( this ), ScalaRef( init ))
         res.writeInit()( this )
         res
      }

      def newLongVar( id: S#ID, init: Long ): S#Var[ Long ] = {
         val res = new LongVar( system.newIDValue()( this ))
         res.setInit( init )( this )
         res
      }

      def newCachedLongVar( init: Long ): S#Var[ Long ] = {
         val res = new CachedLongVar( system.newIDValue()( this ), ScalaRef( init ))
         res.writeInit()( this )
         res
      }

      def newVarArray[ A ]( size: Int ) : Array[ S#Var[ A ] ] = new Array[ Var[ A ]]( size )

      def newInMemoryIDMap[ A ] : IdentifierMap[ S#Tx, S#ID, A ] =
         IdentifierMap.newInMemoryIntMap( _.id )

      def newDurableIDMap[ A ]( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#Tx, S#ID, A ] =
         new IDMapImpl[ A ]( system.newIDValue()( this ))

//      def readVal[ A ]( id: S#ID )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : A = {
//         system.read( id.id )( serializer.read( _, () )( this ))( this )
//      }
//
//      def writeVal[ A ]( id: S#ID, value: A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) {
//         val idi = id.id
//         if( !system.exists( idi )( this )) {
//            system.write( idi )( serializer.write( value, _ ))( this )
//         }
//      }

      def readVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val id = in.readInt()
         new VarImpl[ A ]( id, ser )
      }

      def readPartialVar[ A ]( pid: S#ID, in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] =
         readVar( pid, in )

      def readCachedVar[ A ]( in: DataInput )( implicit ser: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Var[ A ] = {
         val id = in.readInt()
         val res = new CachedVarImpl[ A ]( id, ScalaRef.make[ A ](), ser )
         res.readInit()( this )
         res
      }

      def readBooleanVar( pid: S#ID, in: DataInput ) : S#Var[ Boolean ] = {
         val id = in.readInt()
         new BooleanVar( id )
      }

      def readIntVar( pid: S#ID, in: DataInput ) : S#Var[ Int ] = {
         val id = in.readInt()
         new IntVar( id )
      }

      def readCachedIntVar( in: DataInput ) : S#Var[ Int ] = {
         val id = in.readInt()
         val res = new CachedIntVar( id, ScalaRef( 0 ))
         res.readInit()( this )
         res
      }

      def readLongVar( pid: S#ID, in: DataInput ) : S#Var[ Long ] = {
         val id = in.readInt()
         new LongVar( id )
      }

      def readCachedLongVar( in: DataInput ) : S#Var[ Long ] = {
         val id = in.readInt()
         val res = new CachedLongVar( id, ScalaRef( 0L ))
         res.readInit()( this )
         res
      }

      def readID( in: DataInput, acc: S#Acc ) : S#ID = new IDImpl( in.readInt() )
      def readPartialID( in: DataInput, acc: S#Acc ) : S#ID = readID( in, acc )

      def access[ A ]( source: S#Var[ A ]) : A = source.get( this )
   }

   private final class System( /* private[stm] val */ store: DataStore ) // , idCnt0: Int, reactCnt0: Int
   extends Durable {
      system =>

      private val (idCntVar, reactCntVar) = step { implicit tx =>
         val _id        = store.get( _.writeInt( 0 ))( _.readInt() ).getOrElse( 1 )
         val _react     = store.get( _.writeInt( 1 ))( _.readInt() ).getOrElse( 0 )
         val _idCnt     = ScalaRef( _id )
         val _reactCnt  = ScalaRef( _react )
         (new CachedIntVar( 0, _idCnt ),
          new CachedIntVar( 1, _reactCnt ))
      }

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, S ]( reactCntVar )

//      def manifest: Manifest[ S ] = Manifest.classType( classOf[ Durable ])
//      def idOrdering : Ordering[ S#ID ] = IDOrdering

//      def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ] = v

      def root[ A ]( init: S#Tx => A )( implicit serializer: TxnSerializer[ S#Tx, S#Acc, A ]) : S#Entry[ A ] = {
         val rootID = 2 // 1 == reaction map!!!
         step { implicit tx =>
            if( exists( rootID )) {
//               read( rootID )( ser.read( _, () ))
               new VarImpl[ A ]( rootID, serializer )

            } else {
               val id = newIDValue()
               require( id == rootID, "Root can only be initialized on an empty database (expected id count is " + rootID + " but found " + id + ")")
               val res = new VarImpl[ A ]( id, serializer )
               res.setInit( init( tx ))
//               write( id )( ser.write( res, _ ))
               res
            }
         }
      }

      // ---- cursor ----

      def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic( itx => fun( new TxnImpl( this, itx )))
      }

      def position( implicit tx: S#Tx ) : S#Acc = ()

      def position_=( path: S#Acc )( implicit tx: S#Tx ) {}

      def debugListUserRecords()( implicit tx: S#Tx ): Seq[ ID ] = {
         val b    = Seq.newBuilder[ ID ]
         val cnt  = idCntVar.get
         var i    = 1
         while( i <= cnt ) {
            if( exists( i )) b += new IDImpl( i )
            i += 1
         }
         b.result()
      }

      def close() {
         store.close()
      }

      def wrap( peer: InTxn ) : S#Tx = new TxnImpl( this, peer )

      def numRecords( implicit tx: S#Tx ): Int = store.numEntries

      def numUserRecords( implicit tx: S#Tx ): Int = math.max( 0, numRecords - 1 )

      def newIDValue()( implicit tx: S#Tx ) : Int = {
         val id = idCntVar.get + 1
         logSTM( "new   <" + id + ">" )
         idCntVar.set( id )
         id
      }

      def write( id: Long )( valueFun: DataOutput => Unit )( implicit tx: S#Tx ) {
         logSTM( "writeL <" + id + ">" )
         store.put( _.writeLong( id ))( valueFun )
      }

      def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: S#Tx ) {
         logSTM( "write <" + id + ">" )
         store.put( _.writeInt( id ))( valueFun )
      }

      def remove( id: Long )( implicit tx: S#Tx ) {
         logSTM( "removL <" + id + ">" )
         store.remove( _.writeLong( id ))
      }

      def remove( id: Int )( implicit tx: S#Tx ) {
         logSTM( "remov <" + id + ">" )
         store.remove( _.writeInt( id ))
      }

      def tryRead[ A ]( id: Long )( valueFun: DataInput => A )( implicit tx: S#Tx ) : Option[ A ]= {
         logSTM( "readL  <" + id + ">" )
         store.get( _.writeLong( id ))( valueFun )
      }

      def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: S#Tx ) : A = {
         logSTM( "read  <" + id + ">" )
         store.get( _.writeInt( id ))( valueFun ).getOrElse( sys.error( "Key not found " + id ))
      }

      def exists( id: Int )( implicit tx: S#Tx ) : Boolean = store.contains( _.writeInt( id ))

      def exists( id: Long )( implicit tx: S#Tx ) : Boolean = store.contains( _.writeLong( id ))
   }
}

sealed trait Durable extends Sys[ Durable ] with Cursor[ Durable ] {
   final type Var[ @specialized A ] = Durable.Var[ A ]
   final type ID                    = Durable.ID
   final type Tx                    = Durable.Txn // Txn[ Durable ]
   final type Acc                   = Unit
   final type Entry[ A ]            = Durable.Var[ A ]

   /**
    * Reports the current number of records stored in the database.
    */
   def numRecords( implicit tx: Tx ) : Int

   /**
    * Reports the current number of user records stored in the database.
    * That is the number of records minus those records used for
    * database maintenance.
    */
   def numUserRecords( implicit tx: Tx ) : Int

   def debugListUserRecords()( implicit tx: Tx ) : Seq[ ID ]

//   private[stm] def store : DataStore

   private[stm] def read[ @specialized A ]( id: Int )( valueFun: DataInput => A )( implicit tx: Tx ): A

   private[stm] def tryRead[ A ]( id: Long )( valueFun: DataInput => A )( implicit tx: Tx ): Option[ A ]

   private[stm] def write( id: Int )( valueFun: DataOutput => Unit )( implicit tx: Tx ): Unit

   private[stm] def write( id: Long )( valueFun: DataOutput => Unit )( implicit tx: Tx ): Unit

   private[stm] def remove( id: Int )( implicit tx: Tx ) : Unit

   private[stm] def remove( id: Long )( implicit tx: Tx ) : Unit

   private[stm] def exists( id: Int )( implicit tx: Tx ) : Boolean

   private[stm] def exists( id: Long )( implicit tx: Tx ) : Boolean

   private[stm] def newIDValue()( implicit tx: Tx ) : Int

   def wrap( peer: InTxn ) : Tx  // XXX TODO this might go in Cursor?
}
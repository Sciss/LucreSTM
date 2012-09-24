package de.sciss.lucre
package stm
package impl

import collection.immutable.{IndexedSeq => IIdxSeq, IntMap}
import util.MurmurHash
import event.ReactionMap
import concurrent.stm.{InTxn, TxnExecutor}

/**
 * A simple confluent system implementation for testing purposes only. It is not really
 * transactional (atomic), nor any thread safe, nor does it have particular performance
 * guarantees. Use it exclusively for trying out if things work under confluent semantics,
 * but don't expect wonders. For a production quality system, see the separate
 * TemporalObjects project instead.
 */
sealed trait ConfluentSkel extends Sys[ ConfluentSkel ] with Cursor[ ConfluentSkel ] {
   import ConfluentSkel._

   final type Var[ @specialized A ] = ConfluentSkel.Var[ A ]
   final type ID                    = ConfluentSkel.ID
   final type Tx                    = ConfluentSkel.Txn
   final type Acc                   = IIdxSeq[ Int ]
   final type Entry[ A ]            = ConfluentSkel.Var[ A ]

   def inPath[ A ]( _path: Acc )( fun: Tx => A ) : A
   def fromPath[ A ]( _path: Acc )( fun: Tx => A ) : A
//   def path( implicit tx: Tx ) : Acc

//   def update[ A <: Mutable[ ConfluentSkel ]]( old: A )( implicit tx: Tx, reader: MutableReader[ ID, Txn, A ]) : A
}

object ConfluentSkel {
   private type Acc = IIdxSeq[ Int ]
   private type S = ConfluentSkel

   private type M = Map[ Acc, Array[ Byte ] ]

   sealed trait ID extends Identifier[ Txn ] {
      private[ ConfluentSkel ] def id: Int

      private[ ConfluentSkel ] def path: Acc

      final def shortString: String = path.mkString("<", ",", ">")

      override def hashCode = {
         import MurmurHash._
         var h = startHash(2)
         val c = startMagicA
         val k = startMagicB
         h = extendHash(h, id, c, k)
         h = extendHash(h, path.##, nextMagicA(c), nextMagicB(k))
         finalizeHash(h)
      }

      override def equals( that: Any ): Boolean =
         that.isInstanceOf[ ID ] && {
            val b = that.asInstanceOf[ ID ]
            id == b.id && path == b.path
         }
   }

   sealed trait Txn extends stm.Txn[ S ] {
//      private [ConfluentSkel] def markDirty() : Unit
   }

   sealed trait Var[ @specialized A ] extends stm.Var[ Txn, A ] {
      //      private[ConfluentSkel] def access( path: S#Acc )( implicit tx: S#Tx ) : A
   }

   def apply(): S = new System

   //   private object IDOrdering extends Ordering[ S#ID ] {
   //      def compare( a: S#ID, b: S#ID ) : Int = {
   //         val aid = a.id
   //         val bid = b.id
   //         if( aid < bid ) -1 else if( aid > bid ) 1 else {
   //            val ap      = a.path
   //            val bp      = b.path
   //            val apsz    = ap.size
   //            val bpsz    = bp.size
   //            val minsz = math.min( apsz, bpsz )
   //            var i = 0; while ( i < minsz ) {
   //               val av = ap( i )
   //               val bv = bp( i )
   //               if( av < bv ) return -1 else if( av > bv ) return 1
   //            i += 1 }
   //            if( apsz < bpsz ) -1 else if( apsz > bpsz ) 1 else 0
   //         }
   //      }
   //   }

   private final class System extends ConfluentSkel {
      private var cnt = 0
      private var pathVar = IIdxSeq.empty[ Int ]

      var storage = IntMap.empty[ M ]
      val inMem = InMemory()

      val reactionMap: ReactionMap[ S ] = ReactionMap[ S, InMemory ]( inMem.step { implicit tx =>
         tx.newIntVar( tx.newID(), 0 )
      })( ctx => inMem.wrap( ctx.peer ))

      //      def manifest: Manifest[ S ] = Manifest.classType( classOf[ ConfluentSkel ])
      //      def idOrdering : Ordering[ S#ID ] = IDOrdering

      //      def asEntry[ A ]( v: S#Var[ A ]) : S#Entry[ A ] = v

      def root[ A ]( init: S#Tx => A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]): S#Entry[ A ] = {
         step {implicit tx =>
            tx.newVar[ A ]( tx.newID(), init( tx ))
         }
      }

      def close() {}

      def inPath[ Z ]( path: Acc )( block: Tx => Z ): Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
            val oldPath = pathVar
            try {
               pathVar = path
               block( new TxnImpl( this, itx ))
            } finally {
               pathVar = oldPath
            }
         }
      }

      def access( id: Int, acc: S#Acc )( implicit tx: Txn ): (DataInput, S#Acc) = {
         var best: Array[ Byte ] = null
         var bestLen = 0
         val map = storage.getOrElse(id, Map.empty)
         map.foreach {
            case (path, arr) =>
               val len = path.zip( acc ).segmentLength({ case (a, b) => a == b }, 0 )
               if( len > bestLen && len == path.size ) {
                  best = arr
                  bestLen = len
               }
         }
         require( best != null, "No value for path " + acc )
         val in = new DataInput( best )
         (in, acc.drop( bestLen ))
      }

      def fromPath[ A ]( path: Acc )( fun: Tx => A ): A = {
         TxnExecutor.defaultAtomic[ A ] { itx =>
            pathVar = path :+ (pathVar.lastOption.getOrElse( -1 ) + 1)
            fun( new TxnImpl( this, itx ))
         }
      }

      // ---- cursor ----

      def step[ A ]( fun: S#Tx => A ): A = {
         TxnExecutor.defaultAtomic[ A ] { itx =>
            pathVar :+= (pathVar.lastOption.getOrElse( -1 ) + 1)
            fun( new TxnImpl( this, itx ))
         }
      }

      def position( implicit tx: S#Tx ): S#Acc = pathVar

      def newIDCnt()( implicit tx: Tx ): Int = {
         val id = cnt
         cnt += 1
         id
      }

      def newID()( implicit tx: Tx ): ID = {
         val id = newIDCnt()
         new IDImpl( id, pathVar.takeRight( 1 ))
      }

//      def update[ A <: Mutable[ S ] ]( old: A )( implicit tx: Tx, reader: MutableReader[ ID, Txn, A ]): A = {
//         val out = new DataOutput()
//         old.write( out )
//         val in = new DataInput( out.toByteArray )
//         val mid = in.readInt()
//         val newID = IDImpl.readAndUpdate( mid, position, in )
//         reader.readData( in, newID )
//      }
   }

   private[ ConfluentSkel ] def opNotSupported( name: String ): Nothing = sys.error( "Operation not supported: " + name )

   private object IDImpl {
      def readPath( in: DataInput ): Acc = {
         val sz = in.readInt()
         IIdxSeq.fill( sz )( in.readInt() )
      }

      def readAndAppend( id: Int, postfix: Acc, in: DataInput ): ID = {
         val path = readPath( in )
         val newPath = path ++ postfix // accessPath.drop( com )
         new IDImpl( id, newPath )
      }

      def readAndReplace( id: Int, newPath: Acc, in: DataInput ): ID = {
         readPath( in ) // just ignore it
         new IDImpl( id, newPath )
      }

      def readAndUpdate( id: Int, accessPath: Acc, in: DataInput ): ID = {
         val sz = in.readInt()
         val path = IIdxSeq.fill( sz )( in.readInt() )
         val newPath = path :+ accessPath.last
         new IDImpl( id, newPath )
      }
   }

   private final class IDImpl( val id: Int, val path: Acc ) extends ID {
      def write( out: DataOutput ) {
         out.writeInt( id )
         out.writeInt( path.size )
         path.foreach( out.writeInt( _ ))
      }

      override def toString = "<" + id + path.mkString( " @ ", ",", ">" )

      def dispose()( implicit tx: Txn ) {}
   }

   private final class IDMapImpl[ A ]( val id: S#ID )( implicit serializer: Serializer[ S#Tx, S#Acc, A ])
   extends IdentifierMap[ S#ID, S#Tx, A ] {
      def get( id: S#ID )( implicit tx: S#Tx ): Option[ A ] = {
         sys.error( "TODO" )
      }

      def getOrElse( id: S#ID, default: => A )( implicit tx: S#Tx ): A = {
         sys.error( "TODO" )
      }

      def put( id: S#ID, value: A )( implicit tx: S#Tx ) {
         sys.error( "TODO" )
      }

      def contains( id: S#ID )( implicit tx: S#Tx ): Boolean = {
         sys.error( "TODO" )
      }

      def remove( id: S#ID )( implicit tx: S#Tx ) {
         sys.error( "TODO" )
      }

      def write( out: DataOutput ) {
         id.write( out )
      }

      def dispose()( implicit tx: S#Tx ) {
         id.dispose()
      }

      override def toString = "IdentifierMap" + id // <" + id + ">"
   }

   private final class TxnImpl( val system: System, val peer: InTxn ) extends Txn {
      lazy val inMemory: InMemory#Tx = system.inMem.wrap( peer )

//      private var dirty = false
//
//      def isDirty = dirty
//
//      def markDirty() { dirty = true }

      def newID(): ID = system.newID()( this )

      def newPartialID(): S#ID = sys.error( "TODO" )

      override def toString = "ConfluentSkel#Tx" // + system.path.mkString( "<", ",", ">" )

      def reactionMap: ReactionMap[ S ] = system.reactionMap

      def alloc( pid: ID ): ID = new IDImpl( system.newIDCnt()( this ), pid.path )

      def newVar[ A ]( pid: ID, init: A )( implicit ser: Serializer[ Txn, Acc, A ]): Var[ A ] = {
         val id = alloc( pid )
         val res = new VarImpl[ A ]( id, system, ser )
         res.store( init )( this )
         res
      }

      def newLocalVar[ A ]( init: S#Tx => A ) : LocalVar[ S#Tx, A ] = new impl.LocalVarImpl[ S, A ]( init )

      def newPartialVar[ A ]( id: S#ID, init: A )( implicit ser: Serializer[ S#Tx, S#Acc, A ]): S#Var[ A ] =
         sys.error( "TODO" )

      def newBooleanVar( pid: ID, init: Boolean ): Var[ Boolean ] = newVar[ Boolean ]( pid, init )

      def newIntVar( pid: ID, init: Int ): Var[ Int ] = newVar[ Int ]( pid, init )

      def newLongVar( pid: ID, init: Long ): Var[ Long ] = newVar[ Long ]( pid, init )

      def newVarArray[ A ]( size: Int ) = new Array[ Var[ A ]]( size )

      def newInMemoryIDMap[ A ]: IdentifierMap[ S#ID, S#Tx, A ] = sys.error( "TODO" )

      def newDurableIDMap[ A ]( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ]  =
         new IDMapImpl[ A ]( newID() )

      private def readSource( in: DataInput, pid: ID ): ID = {
         val id = in.readInt()
         new IDImpl( id, pid.path )
      }

      def readVar[ A ]( pid: ID, in: DataInput )( implicit ser: Serializer[ Txn, Acc, A ]): Var[ A ] = {
         val id = readSource( in, pid )
         new VarImpl( id, system, ser )
      }

      def readPartialVar[ A ]( pid: ID, in: DataInput )( implicit ser: Serializer[ Txn, Acc, A ]): Var[ A ] =
         sys.error( "TODO" )

      def readBooleanVar( pid: ID, in: DataInput ): Var[ Boolean ] = readVar[ Boolean ]( pid, in )

      def readIntVar( pid: ID, in: DataInput ): Var[ Int ] = readVar[ Int ]( pid, in )

      def readLongVar( pid: ID, in: DataInput ): Var[ Long ] = readVar[ Long ]( pid, in )

      def readID( in: DataInput, acc: Acc ): ID = IDImpl.readAndAppend( in.readInt(), acc, in )

      def readPartialID( in: DataInput, aPacc: S#Acc ): S#ID = sys.error( "TODO" )

      def readDurableIDMap[ A ]( in: DataInput )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]) : IdentifierMap[ S#ID, S#Tx, A ] =
         sys.error( "TODO" )

      def refresh[ A ]( access: S#Acc, value: A )( implicit serializer: Serializer[ S#Tx, S#Acc, A ]): A = {
         val out = new DataOutput()
         serializer.write( value, out )
         val newAcc = system.position( this )
         val len = access.zip( newAcc ).segmentLength({ case (a, b) => a == b }, 0 )
         val in = new DataInput( out )
         val postfix = newAcc.drop( len )
         serializer.read( in, postfix )( this )
      }
   }

   private sealed trait SourceImpl[ @specialized A ] {
      protected def id: ID

      protected def system: System

      protected final def toString( pre: String ) = pre + id + ": " +
         (system.storage.getOrElse( id.id, Map.empty ).map( _._1 )).mkString( ", " )

      final def set( v: A )( implicit tx: Txn ) {
         store( v )
      }

      final def write( out: DataOutput ) {
         out.writeInt( id.id )
      }

      protected def writeValue( v: A, out: DataOutput ): Unit

      protected def readValue( in: DataInput, postfix: Acc )( implicit tx: Txn ): A

      final def store( v: A )( implicit tx: Txn ) {
         val out = new DataOutput()
         writeValue( v, out )
         val bytes = out.toByteArray
         system.storage += id.id -> (system.storage.getOrElse( id.id,
            Map.empty[ Acc, Array[ Byte ]]) + (id.path -> bytes))
//         tx.markDirty()
      }

      final def get( implicit tx: Txn ): A = access( id.path )

      private def access( acc: S#Acc )( implicit tx: Txn ): A = {
         val (in, acc1) = system.access( id.id, acc )
         readValue( in, acc1 )
      }

      final def transform( f: A => A )( implicit tx: Txn ) {
         set( f( get ))
      }

      final def dispose()( implicit tx: Txn ) {}
   }

   private final class VarImpl[ @specialized A ]( val id: ID, val system: System,
                                                  ser: Serializer[ Txn, Acc, A ])
      extends Var[ A ] with SourceImpl[ A ] {

      override def toString = toString( "Var" )

      protected def writeValue( v: A, out: DataOutput ) {
         ser.write( v, out )
      }

      protected def readValue( in: DataInput, postfix: Acc )( implicit tx: Txn ): A = {
         ser.read( in, postfix )
      }

      def isFresh( implicit tx: S#Tx ): Boolean = {
         sys.error( "TODO" )
         //         val (_, acc) = system.access( id.id, id.path )
         //         acc.last == id.path.last   // XXX overly pessimistic, but we haven't implemented meld or tree levels here, anyway
      }
   }
}
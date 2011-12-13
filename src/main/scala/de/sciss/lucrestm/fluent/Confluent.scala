package de.sciss.lucrestm
package fluent

import de.sciss.lucrestm.{ Txn => _Txn, Ref => _Ref, Val => _Val }
import concurrent.stm.{InTxn, TxnExecutor}
import collection.immutable.{IndexedSeq => IIdxSeq}

object Confluent {
   sealed trait ID extends Identifier[ Txn ] {
      private[Confluent] def id: Int
      private[Confluent] def path: IIdxSeq[ Int ]
   }

   sealed trait Txn extends _Txn[ Confluent ]
   sealed trait Val[ @specialized A ] extends _Val[ Txn, A ]
   sealed trait Ref[ A ] extends _Ref[ Txn, A ]

   def apply() : Confluent = new System

   private final class System extends Confluent {

   }

   private[Confluent] def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   private final class IDImpl( val id: Int, val path: IIdxSeq[ Int ]) extends ID {
      def write( out: DataOutput ) {
         out.writeInt( id )
         out.writeInt( path.size )
         path.foreach( out.writeInt( _ ))
      }

      override def toString = "<"  + id + path.mkString( " @ ", ",", ">" )

      def dispose()( implicit tx: Txn ) {}
   }

   private final class TxnImpl( val system: Confluent, val peer: InTxn ) extends Txn {
      private[lucrestm] def newVal[ A ]( id: ID, init: A )( implicit ser: Serializer[ A ]) : Val[ A ] = {
         new ValImpl[ A ]( id, init )
      }

      private[lucrestm] def newInt( id: ID, init: Int ) : Val[ Int ] = {
         new ValImpl( id, init )
      }

      private[lucrestm] def newRef[ A <: Mutable[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableReader[ Confluent, A ]) : Ref[ A ] = {
         new RefImpl[ A ]( id, init )( Serializer.fromMutableReader[ Confluent, A ]( reader, system ))
      }

      private[lucrestm] def newOptionRef[ A <: MutableOption[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableOptionReader[ Confluent, A ]) : Ref[ A ] = {

         opNotSupported( "newOptionRef" )
//         new RefImpl[ A ]( init )( Serializer.fromMutableReader[ Confluent, A ]( reader, system ))
      }

      private[lucrestm] def newValArray[ A ]( size: Int ) = new Array[ Val[ A ]]( size )
      private[lucrestm] def newRefArray[ A ]( size: Int ) = new Array[ Ref[ A ]]( size )
   }

   private sealed trait SourceImpl[ @specialized A ] {
      protected def ser: Serializer[ A ]
      protected def id: ID

//      final var bytes: Array[ Byte ] = null
      private final var set = Map.empty[ IIdxSeq[ Int ], Array[ Byte ]]

      final def set( v: A )( implicit tx: Txn ) {
         writeValue( v )
      }

      protected final def writeValue( v: A ) {
         val out = new DataOutput()
         ser.write( v, out )
         val bytes = out.toByteArray
         set += id.path -> bytes
      }

      final def get( implicit tx: Txn ) : A = {
         var best: Array[Byte]   = null
         var bestLen = 0
         set.foreach {
            case (path, arr) =>
               val len = path.zip( id.path ).segmentLength({ case (a, b) => a == b }, 0 )
               if( len > bestLen ) {
                  best     = arr
                  bestLen  = len
               }
         }
         require( best != null, "No value for path " + id.path )
         val in = new DataInput( best )
         ser.read( in )
      }

      final def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      final def dispose()( implicit tx: Txn ) { set = set.empty }
   }

   private final class RefImpl[ A ]( val id: ID, init: A )( implicit val ser: Serializer[ A ])
   extends Ref[ A ] with SourceImpl[ A ] {
      writeValue( init )

      override def toString = "Ref" + id

      def write( out: DataOutput ) {
         id.write( out )
      }
   }

   private final class ValImpl[ @specialized A ]( val id: ID, init: A )( implicit val ser: Serializer[ A ])
   extends Val[ A ] with SourceImpl[ A ] {
      writeValue( init )

      override def toString = "Val" + id

      def write( out: DataOutput ) {
         id.write( out )
      }
   }
}
sealed trait Confluent extends Sys[ Confluent ] {
   import Confluent._

   type Val[ @specialized A ]  = Confluent.Val[ A ]
   type Ref[ A ]  = Confluent.Ref[ A ]
   type ID        = Confluent.ID
   type Tx        = Confluent.Txn

   def manifest: Manifest[ Confluent ] = Manifest.classType( classOf[ Confluent ])

   private var cnt = 0

   def newID()( implicit tx: Tx ) : ID = {
      val id = cnt
      cnt += 1
      new IDImpl( id, IIdxSeq.empty )
   }

   def atomic[ Z ]( block: Tx => Z ) : Z = {
      TxnExecutor.defaultAtomic[ Z ]( itx => block( new TxnImpl( this, itx )))
   }

   def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Val[ A ] = {
      opNotSupported( "readVal" )
   }

   def readInt( in: DataInput ) : Val[ Int ] = {
      opNotSupported( "readIntVal" )
   }

   def readRef[ A <: Mutable[ Confluent ]]( in: DataInput )
                                          ( implicit reader: MutableReader[ Confluent, A ]) : Ref[ A ] = {
      opNotSupported( "readRef" )
   }

   def readOptionRef[ A <: MutableOption[ Confluent ]]( in: DataInput )
                                                      ( implicit reader: MutableOptionReader[ Confluent, A ]) : Ref[ A ] = {
      opNotSupported( "readOptionRef" )
   }

   def readMut[ A <: Mutable[ Confluent ]]( in: DataInput )( implicit reader: MutableReader[ Confluent, A ]) : A = {
      opNotSupported( "readMut" )
   }

   def readOptionMut[ A <: MutableOption[ Confluent ]]( in: DataInput )
                                                      ( implicit reader: MutableOptionReader[ Confluent, A ]) : A = {
      opNotSupported( "readOptionMut" )
   }
}
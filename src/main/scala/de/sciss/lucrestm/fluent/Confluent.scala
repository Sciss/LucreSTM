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

   private object IDImpl {
      def readAndSubstitute( id: Int, access: ID, in: DataInput ) : ID = {
         val sz      = in.readInt()
         val path    = IIdxSeq.fill( sz )( in.readInt() )
         val com     = path.zip( access.path ).segmentLength({ case (a, b) => a == b }, 0 )
         val newPath = path ++ access.path.drop( com )
         new IDImpl( access.id, newPath )
      }
   }
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
      def newVal[ A ]( id: ID, init: A )( implicit ser: Serializer[ A ]) : Val[ A ] = {
         val res = new ValImpl[ A ]( id, ser )
         res.write( init )
         res
      }

      def newInt( id: ID, init: Int ) : Val[ Int ] = {
         val res = new ValImpl( id, Serializer.Int )
         res.write( init )
         res
      }

      def newRef[ A <: Mutable[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableReader[ Confluent, A ]) : Ref[ A ] = {
         val res = new RefImpl[ A ]( id, reader )
         res.write( init )
         res
      }

      def newOptionRef[ A <: MutableOption[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableOptionReader[ Confluent, A ]) : Ref[ A ] = {

         val res = new RefOptionImpl[ A ]( id, reader )
         res.write( init )
         res
      }

      def newValArray[ A ]( size: Int ) = new Array[ Val[ A ]]( size )
      def newRefArray[ A ]( size: Int ) = new Array[ Ref[ A ]]( size )

      def readVal[ A ]( pid: ID, in: DataInput )( implicit ser: Serializer[ A ]) : Val[ A ] = {
         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         new ValImpl( id, ser )
      }

      def readInt( pid: ID, in: DataInput ) : Val[ Int ] = {
         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         new ValImpl( id, Serializer.Int )
      }

      def readRef[ A <: Mutable[ Confluent ]]( pid: ID, in: DataInput )
                                             ( implicit reader: MutableReader[ Confluent, A ]) : Ref[ A ] = {
         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         new RefImpl( id, reader )
      }

      def readOptionRef[ A <: MutableOption[ Confluent ]]( pid: ID, in: DataInput )(
         implicit reader: MutableOptionReader[ Confluent, A ]) : Ref[ A ] = {

         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         new RefOptionImpl( id, reader )
      }

      def readMut[ A <: Mutable[ Confluent ]]( pid: ID, in: DataInput )
                                             ( implicit reader: MutableReader[ Confluent, A ]) : A = {
         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         reader.readData( in, id )
      }

      def readOptionMut[ A <: MutableOption[ Confluent ]]( pid: ID, in: DataInput )
                                                         ( implicit reader: MutableOptionReader[ Confluent, A ]) : A = {
         val mid  = in.readInt()
         if( mid == -1 ) reader.empty else {
            val id   = IDImpl.readAndSubstitute( mid, pid, in )
            reader.readData( in, id )
         }
      }
   }

   private sealed trait SourceImpl[ @specialized A ] extends Serializer[ A ] {
//      protected def ser: Serializer[ A ]
      protected def id: ID

//      final var bytes: Array[ Byte ] = null
      private final var set = Map.empty[ IIdxSeq[ Int ], Array[ Byte ]]

      final def set( v: A )( implicit tx: Txn ) {
         write( v )
      }

//      protected def writeValue( v: A, out: DataOutput ) : Unit
//      protected def readValue( in: DataInput ) : Unit

      final def write( v: A ) {
         val out = new DataOutput()
         write( v, out )
//         ser.write( v, out )
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
//         ser.read( in )
         read( in )
      }

      final def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      final def dispose()( implicit tx: Txn ) { set = set.empty }
   }

   private final class RefImpl[ A <: Mutable[ Confluent ]]( val id: ID, reader: MutableReader[ Confluent, A ])
   extends Ref[ A ] with SourceImpl[ A ] {

      override def toString = "Ref" + id

      def write( out: DataOutput ) {
         id.write( out )
      }

      def write( v: A, out: DataOutput ) {
         v.write( out )
      }

      def read( in: DataInput ) : A = {
         val mid = in.readInt()
         reader.readData( in, IDImpl.readAndSubstitute( mid, id, in ))
      }
   }

   private final class RefOptionImpl[ A <: MutableOption[ Confluent ]]( val id: ID,
                                                                        reader: MutableOptionReader[ Confluent, A ])
   extends Ref[ A ] with SourceImpl[ A ] {

      override def toString = "Ref" + id

      def write( out: DataOutput ) {
         id.write( out )
      }

      def write( v: A, out: DataOutput ) {
         v match {
            case m: Mutable[ _ ] => m.write( out )
            case _: EmptyMutable => out.writeInt( -1 )
         }
      }

      def read( in: DataInput ) : A = {
         val mid = in.readInt()
         if( mid == -1 ) reader.empty else {
            reader.readData( in, IDImpl.readAndSubstitute( mid, id, in ))
         }
      }
   }

   private final class ValImpl[ @specialized A ]( val id: ID, ser: Serializer[ A ])
   extends Val[ A ] with SourceImpl[ A ] {


      override def toString = "Val" + id

      def write( out: DataOutput ) {
         id.write( out )
      }

      def write( v: A, out: DataOutput ) {
         ser.write( v, out )
      }

      def read( in: DataInput ) : A = {
         ser.read( in )
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
}
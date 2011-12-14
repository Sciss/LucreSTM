package de.sciss.lucrestm
package fluent

import de.sciss.lucrestm.{ Txn => _Txn, Ref => _Ref, Val => _Val }
import concurrent.stm.{InTxn, TxnExecutor}
import collection.immutable.{IndexedSeq => IIdxSeq}

object Confluent {
   private type M = Map[ IIdxSeq[ Int ], Array[ Byte ]]

   sealed trait ID extends Identifier[ Txn ] {
      private[Confluent] def id: Int
      private[Confluent] def path: IIdxSeq[ Int ]
      final def shortString : String = path.mkString( "<", ",", ">" )
   }

   sealed trait Txn extends _Txn[ Confluent ]
   sealed trait Val[ @specialized A ] extends _Val[ Txn, A ]
   sealed trait Ref[ A ] extends _Ref[ Txn, A ]

   def apply() : Confluent = new System

   private final class System extends Confluent {
      private var cnt = 0
      private var pathVar = IIdxSeq.empty[ Int ]

      def path( implicit tx: Tx ) = pathVar

      def inPath[ Z ]( path: IIdxSeq[ Int ])( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
//            path +:= (path.lastOption.getOrElse( -1 ) + 1)
            val oldPath = pathVar
            try {
               pathVar = path
               block( new TxnImpl( this, itx ))
            } finally {
               pathVar = oldPath
            }
         }
      }

      def fromPath[ Z ]( path: IIdxSeq[ Int ])( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
            pathVar = path :+ (pathVar.lastOption.getOrElse( -1 ) + 1)
            block( new TxnImpl( this, itx ))
         }
      }

      def atomic[ Z ]( block: Tx => Z ) : Z = {
         TxnExecutor.defaultAtomic[ Z ] { itx =>
            pathVar :+= (pathVar.lastOption.getOrElse( -1 ) + 1)
            block( new TxnImpl( this, itx ))
         }
      }

      def newID()( implicit tx: Tx ) : ID = {
         val id = cnt
         cnt += 1
         new IDImpl( id, pathVar.takeRight( 1 ))
      }

      def updateID( old: ID )( implicit tx: Tx ) : ID = IDImpl.substitute( old, path )

      def manifest: Manifest[ Confluent ] = Manifest.classType( classOf[ Confluent ])
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

      def substitute( old: ID, accessPath: IIdxSeq[ Int ]) : ID = {
         val com     = old.path.zip( accessPath ).segmentLength({ case (a, b) => a == b }, 0 )
         val newPath = old.path ++ accessPath.drop( com )
         new IDImpl( old.id, newPath )
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

   private final class TxnImpl( val system: System, val peer: InTxn ) extends Txn {
      def newID() : ID = system.newID()( this )

      def newVal[ A ]( id: ID, init: A )( implicit ser: TxnSerializer[ Txn, A ]) : Val[ A ] = {
         val res = new ValImpl[ A ]( id, Map.empty, ser )
         res.write( init )
         res
      }

      def newInt( id: ID, init: Int ) : Val[ Int ] = {
         val res = new ValImpl( id, Map.empty, Serializer.Int )
         res.write( init )
         res
      }

      def newRef[ A <: Mutable[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableReader[ ID, Txn, A ]) : Ref[ A ] = {
         val res = new RefImpl[ A ]( id, Map.empty, reader )
         res.write( init )
         res
      }

      def newOptionRef[ A <: MutableOption[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableOptionReader[ ID, Txn, A ]) : Ref[ A ] = {

         val res = new RefOptionImpl[ A ]( id, Map.empty, reader )
         res.write( init )
         res
      }

      def newValArray[ A ]( size: Int ) = new Array[ Val[ A ]]( size )
      def newRefArray[ A ]( size: Int ) = new Array[ Ref[ A ]]( size )

      private def readSource( pid: ID, in: DataInput ) : (ID, M) = {
         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         val msz  = in.readInt()
         val map  = Seq.fill[ (IIdxSeq[ Int ], Array[ Byte ])]( msz )({
            val sz   = in.readInt()
            val path = IIdxSeq.fill( sz )( in.readInt() )
            val dsz  = in.readInt()
            val data = new Array[ Byte ]( dsz )
            in.read( data )
            (path, data)
         }).toMap
         (id, map)
      }

      def readVal[ A ]( pid: ID, in: DataInput )( implicit ser: TxnSerializer[ Txn, A ]) : Val[ A ] = {
         val (id, map) = readSource( pid, in )
         new ValImpl( id, map, ser )
      }

      def readInt( pid: ID, in: DataInput ) : Val[ Int ] = {
         val (id, map) = readSource( pid, in )
         new ValImpl( id, map, Serializer.Int )
      }

      def readRef[ A <: Mutable[ Confluent ]]( pid: ID, in: DataInput )
                                             ( implicit reader: MutableReader[ ID, Txn, A ]) : Ref[ A ] = {
         val (id, map) = readSource( pid, in )
         new RefImpl( id, map, reader )
      }

      def readOptionRef[ A <: MutableOption[ Confluent ]]( pid: ID, in: DataInput )(
         implicit reader: MutableOptionReader[ ID, Txn, A ]) : Ref[ A ] = {

         val (id, map) = readSource( pid, in )
         new RefOptionImpl( id, map, reader )
      }

      def readMut[ A <: Mutable[ Confluent ]]( pid: ID, in: DataInput )
                                             ( implicit reader: MutableReader[ ID, Txn, A ]) : A = {
         val mid  = in.readInt()
         val id   = IDImpl.readAndSubstitute( mid, pid, in )
         reader.readData( in, id )( this )
      }

      def readOptionMut[ A <: MutableOption[ Confluent ]]( pid: ID, in: DataInput )
                                                         ( implicit reader: MutableOptionReader[ ID, Txn, A ]) : A = {
         val mid  = in.readInt()
         if( mid == -1 ) reader.empty else {
            val id   = IDImpl.readAndSubstitute( mid, pid, in )
            reader.readData( in, id )( this )
         }
      }
   }

   private sealed trait SourceImpl[ @specialized A ] extends TxnSerializer[ Txn, A ] {
//      protected def ser: Serializer[ A ]
      protected def id: ID

      protected final def toString( pre: String ) = pre + id + ": " + map.mkString( ", " )

//      final var bytes: Array[ Byte ] = null
//      private final var set = Map.empty[ IIdxSeq[ Int ], Array[ Byte ]]
      protected def map : M
      protected def map_=( value: M ) : Unit

      final def set( v: A )( implicit tx: Txn ) {
         write( v )
      }

      final def write( out: DataOutput ) {
         id.write( out )
         val coll = map.toIndexedSeq
         out.writeInt( coll.size )
         coll.foreach { case (path, data) =>
            out.writeInt( path.size )
            path.foreach( out.writeInt )
            out.writeInt( data.length )
            out.write( data )
         }
      }

//      protected def writeValue( v: A, out: DataOutput ) : Unit
//      protected def readValue( in: DataInput ) : Unit

      final def write( v: A ) {
         val out = new DataOutput()
         write( v, out )
//         ser.write( v, out )
         val bytes = out.toByteArray
         map += id.path -> bytes
      }

//      protected def write( v: A, out: DataOutput ) : Unit

      final def get( implicit tx: Txn ) : A = {
         var best: Array[Byte]   = null
         var bestLen = 0
         map.foreach {
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
         txnRead( in )
      }

      final def transform( f: A => A )( implicit tx: Txn ) { set( f( get ))}

      final def dispose()( implicit tx: Txn ) { map = map.empty }
   }

   private final class RefImpl[ A <: Mutable[ Confluent ]]( val id: ID, var map: M, reader: MutableReader[ ID, Txn, A ])
   extends Ref[ A ] with SourceImpl[ A ] {

//      override def toString = "Ref" + id
      override def toString = toString( "Ref" )

//      def write( out: DataOutput ) {
//         id.write( out )
//      }

      def write( v: A, out: DataOutput ) {
         v.write( out )
      }

      def txnRead( in: DataInput )( implicit tx: Txn ) : A = {
         val mid = in.readInt()
         reader.readData( in, IDImpl.readAndSubstitute( mid, id, in ))
      }
   }

   private final class RefOptionImpl[ A <: MutableOption[ Confluent ]]( val id: ID, var map: M,
                                                                        reader: MutableOptionReader[ ID, Txn, A ])
   extends Ref[ A ] with SourceImpl[ A ] {

//      override def toString = "Ref" + id
      override def toString = toString( "Ref" )

      def write( v: A, out: DataOutput ) {
         v match {
            case m: Mutable[ _ ] => m.write( out )
            case _: EmptyMutable => out.writeInt( -1 )
         }
      }

      def txnRead( in: DataInput )( implicit tx: Txn ) : A = {
         val mid = in.readInt()
         if( mid == -1 ) reader.empty else {
            reader.readData( in, IDImpl.readAndSubstitute( mid, id, in ))
         }
      }
   }

   private final class ValImpl[ @specialized A ]( val id: ID, var map: M, ser: TxnSerializer[ Txn, A ])
   extends Val[ A ] with SourceImpl[ A ] {

//      override def toString = "Val" + id
      override def toString = toString( "Val" )

      def write( v: A, out: DataOutput ) {
         ser.write( v, out )
      }

      def txnRead( in: DataInput )( implicit tx: Txn ) : A = {
         ser.txnRead( in )
      }
   }
}
sealed trait Confluent extends Sys[ Confluent ] {
   import Confluent._

   type Val[ @specialized A ]  = Confluent.Val[ A ]
   type Ref[ A ]  = Confluent.Ref[ A ]
   type ID        = Confluent.ID
   type Tx        = Confluent.Txn

   def inPath[ Z ]( _path: IIdxSeq[ Int ])( block: Tx => Z ) : Z
   def fromPath[ Z ]( _path: IIdxSeq[ Int ])( block: Tx => Z ) : Z
   def path( implicit tx: Tx ) : IIdxSeq[ Int ]
   def updateID( old: ID )( implicit tx: Tx ) : ID
}
package de.sciss.lucrestm
package fluent

import de.sciss.lucrestm.{ Txn => _Txn, Ref => _Ref, Val => _Val }
import concurrent.stm.{InTxn, TxnExecutor, Ref => ScalaRef}

object Confluent {
   sealed trait ID extends Identifier[ Txn ]
   sealed trait Txn extends _Txn[ Confluent ]
   sealed trait Val[ @specialized A ] extends _Val[ Txn, A ]
   sealed trait Ref[ A ] extends _Ref[ Txn, A ]

   private final class System extends Confluent {

   }

   private[Confluent] def opNotSupported( name: String ) : Nothing = sys.error( "Operation not supported: " + name )

   private final class IDImpl extends ID {
      def write(  out: DataOutput ) {
         opNotSupported( "ID.write" )
      }

      def dispose()( implicit tx: Txn ) {}
   }

   private final class TxnImpl( val system: Confluent, val peer: InTxn ) extends Txn {
      private[lucrestm] def newVal[ A ]( id: ID, init: A )( implicit ser: Serializer[ A ]) : Val[ A ] = {
         val peer = ScalaRef[ A ]( init )
         new ValImpl[ A ]( peer )
      }

      private[lucrestm] def newInt( id: ID, init: Int ) : Val[ Int ] = {
         val peer = ScalaRef( init )
         new ValImpl( peer )
      }

      private[lucrestm] def newRef[ A <: Mutable[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableReader[ Confluent, A ]) : Ref[ A ] = {

         val peer = ScalaRef[ A ]( init )
         new RefImpl[ A ]( peer )
      }

      private[lucrestm] def newOptionRef[ A <: MutableOption[ Confluent ]]( id: ID, init: A )(
         implicit reader: MutableOptionReader[ Confluent, A ]) : Ref[ A ] = {

         val peer = ScalaRef[ A ]( init )
         new RefImpl[ A ]( peer )
      }

      private[lucrestm] def newValArray[ A ]( size: Int ) = new Array[ Val[ A ]]( size )
      private[lucrestm] def newRefArray[ A ]( size: Int ) = new Array[ Ref[ A ]]( size )
   }

   private sealed trait SourceImpl[ @specialized A ] {
      protected def peer: ScalaRef[ A ]
      def set( v: A )( implicit tx: Txn ) { peer.set( v )( tx.peer )}
      def get( implicit tx: Txn ) : A = peer.get( tx.peer )
      def transform( f: A => A )( implicit tx: Txn ) { peer.transform( f )( tx.peer )}
      def dispose()( implicit tx: Txn ) { peer.set( null.asInstanceOf[ A ])( tx.peer )}
      def write( out: DataOutput ) {}
   }

   private final class RefImpl[ A ]( protected val peer: ScalaRef[ A ])
   extends Ref[ A ] with SourceImpl[ A ] {
      override def toString = "Ref<" + hashCode().toHexString + ">"
   }

   private final class ValImpl[ @specialized A ]( protected val peer: ScalaRef[ A ])
   extends Val[ A ] with SourceImpl[ A ] {
      override def toString = "Val<" + hashCode().toHexString + ">"
   }
}
sealed trait Confluent extends Sys[ Confluent ] {
   import Confluent._

   type Val[ @specialized A ]  = Confluent.Val[ A ]
   type Ref[ A ]  = Confluent.Ref[ A ]
   type ID        = Confluent.ID
   type Tx        = Confluent.Txn

   def manifest: Manifest[ Confluent ] = Manifest.classType( classOf[ Confluent ])

   def newID()( implicit tx: Tx ) : ID = new IDImpl

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
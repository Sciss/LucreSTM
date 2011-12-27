package de.sciss.lucrestm
package fluent

import collection.immutable.{IndexedSeq => IIdxSeq}
import java.awt.EventQueue
import javax.swing.{WindowConstants, JFrame}
import concurrent.stm.TMap

object ReactionTest extends App with Runnable {
   EventQueue.invokeLater( this )

   type Tx = Confluent#Tx

   object Reactor {
      implicit def serializer[ S <: Sys[ S ]] : TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] = new Ser[ S ]

      private final class Ser[ S <: Sys[ S ]] extends TxnSerializer[ S#Tx, S#Acc, Reactor[ S ]] {
         def write( r: Reactor[ S ], out: DataOutput ) { r.write( out )}

         def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Reactor[ S ] = {
            if( in.readUnsignedByte() == 0 ) {
               new ReactorBranchRead[ S ]( in, tx, access )
            } else {
               new ReactorLeaf[ S ]( in.readInt() )
            }
         }
      }

      private final class ReactorBranchRead[ S <: Sys[ S ]]( in: DataInput, tx0: S#Tx, acc: S#Acc ) extends ReactorBranch[ S ] {
         val id = tx0.readID( in, acc )
         protected val children = tx0.readVar[ IIdxSeq[ Reactor[ S ]]]( id, in )
      }
   }

   sealed trait Reactor[ S <: Sys[ S ]] extends Writer {
      def propagate()( implicit tx: S#Tx, map: ReactionMap[ S ]) : Unit
   }

   object ReactorLeaf {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx, map: ReactionMap[ S ]) : ReactorLeaf[ S ] = new ReactorLeaf( map.newID() )
   }

   final case class ReactorLeaf[ S <: Sys[ S ]]( id: Int ) extends Reactor[ S ] {
      def write( out: DataOutput ) {
         out.writeUnsignedByte( 1 )
         out.writeInt( id )
      }

      def propagate()( implicit tx: S#Tx, map: ReactionMap[ S ]) {
         map.invoke( this )
      }
   }

   sealed trait Observable[ S <: Sys[ S ]] {
      def addReactor(    r: Reactor[ S ])( implicit tx: S#Tx ) : Unit
      def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) : Unit
   }

   object ReactorBranch {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : ReactorBranch[ S ] = new ReactorBranchNew[ S ]( tx )

      private final class ReactorBranchNew[ S <: Sys[ S ]]( tx0: S#Tx ) extends ReactorBranch[ S ] {
         val id = tx0.newID()
         protected val children = tx0.newVar[ IIdxSeq[ Reactor[ S ]]]( id, IIdxSeq.empty )
      }
   }

   sealed trait ReactorBranch[ S <: Sys[ S ]] extends Reactor[ S ] with Observable[ S ] with Mutable[ S ] {
      final def addReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
         children.transform( _ :+ r )
      }

      final def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) {
         val xs = children.get
         val i = xs.indexOf( r )
         if( i >= 0 ) {
            val xs1 = xs.patch( i, IIdxSeq.empty, 1 )
            children.set( xs1 )
         }
      }

      final def propagate()( implicit tx: S#Tx, map: ReactionMap[ S ]) {
         children.get.foreach( _.propagate() )
      }

      final protected def writeData( out: DataOutput ) {
         out.writeUnsignedByte( 0 )
         children.write( out )
      }

      final protected def disposeData()( implicit tx: S#Tx ) {
         children.dispose()
      }

      protected def children: S#Var[ IIdxSeq[ Reactor[ S ]]]
   }

   trait Event[ S <: Sys[ S ], A ] extends Observable[ S ] {
      protected def reactor: ReactorBranch[ S ]
      def value( implicit tx: S#Tx ) : A

      final def addReactor(    r: Reactor[ S ])( implicit tx: S#Tx ) { reactor.addReactor(    r )}
      final def removeReactor( r: Reactor[ S ])( implicit tx: S#Tx ) { reactor.removeReactor( r )}
   }

   object ReactionMap {
      def apply[ S <: Sys[ S ]]()( implicit tx: S#Tx ) : ReactionMap[ S ] = new Impl[ S ]( tx )

      private final class Impl[ S <: Sys[ S ]]( tx0: S#Tx )extends ReactionMap[ S ] {
         private val map   = TMap.empty[ ReactorLeaf[ S ], S#Tx => Unit ]
         val id            = tx0.newID()
         private val cnt   = tx0.newIntVar( id, 0 )

         def invoke( key: ReactorLeaf[ S ])( implicit tx: S#Tx ) {
            map.get( key )( tx.peer ).foreach( _.apply( tx ))
         }

         def add( key: ReactorLeaf[ S ], fun: S#Tx => Unit )( implicit tx: S#Tx ) {
            map.+=( key -> fun )( tx.peer )
         }

         def remove( key: ReactorLeaf[ S ])( implicit tx: S#Tx ) {
            map.-=( key )( tx.peer )
         }

         def newID()( implicit tx: S#Tx ) : Int = {
            val res = cnt.get
            cnt.set( res + 1 )
            res
         }
      }
   }
   sealed trait ReactionMap[ S <: Sys[ S ]] {
      def newID()( implicit tx: S#Tx ) : Int
      def add(    key: ReactorLeaf[ S ], reaction: S#Tx => Unit )( implicit tx: S#Tx ) : Unit
      def remove( key: ReactorLeaf[ S ])( implicit tx: S#Tx ) : Unit
      def invoke( key: ReactorLeaf[ S ])( implicit tx: S#Tx ) : Unit
   }

   sealed trait Observer { def remove()( implicit tx: Tx ) : Unit }

   trait Expr[ A ] extends Event[ Confluent, A ] with Disposable[ Tx ] {
      def observe( update: A => Unit )( implicit tx: Tx, map: ReactionMap[ Confluent ]) : Observer = {
         val key        = ReactorLeaf.apply[ Confluent ]()
         val reaction   = (tx: Tx) => update( value( tx ))
         map.add( key, reaction )
         addReactor( key )
         new Observer {
            def remove()( implicit tx: Tx ) {
               map.remove( key )
               removeReactor( key )
            }
         }
      }
   }

   trait ConstExpr[ A ] extends Expr[ A ] {
      protected def constValue : A
      final def value( implicit tx: Tx ) : A = constValue
      final def dispose()( implicit tx: Tx ) { reactor.dispose() }
   }

   trait BinaryExpr[ A ] extends Expr[ A ] {
      protected def a: Expr[ A ]
      protected def b: Expr[ A ]
      protected def op( a: A, b: A ) : A

      final def value( implicit tx: Tx ) : A = op( a.value, b.value )

      final def dispose()( implicit tx: Tx ) {
         a.removeReactor( reactor )
         b.removeReactor( reactor )
         reactor.dispose()
      }
   }

   object StringRef {
      implicit def apply( s: String )( implicit tx: Tx ) : StringRef = new StringConstNew( s, tx )

      def append( a: StringRef, b: StringRef )( implicit tx: Tx ) : StringRef =
         new StringAppendNew( a, b, tx )

      private final class StringConstNew( protected val constValue: String, tx0: Tx )
      extends StringRef with ConstExpr[ String ] {
         protected val reactor = ReactorBranch[ Confluent ]()( tx0 )
      }

      private final class StringAppendNew( prefix: StringRef, suffix: StringRef, tx0: Tx )
      extends StringRef {
         protected val reactor = ReactorBranch[ Confluent ]()( tx0 )
         prefix.addReactor( reactor )( tx0 )
         suffix.addReactor( reactor )( tx0 )

         def value( implicit tx: Tx ) : String = prefix.value + suffix.value
         def dispose()( implicit tx: Tx ) {
            prefix.removeReactor( reactor )
            suffix.removeReactor( reactor )
            reactor.dispose()
         }
      }
   }

   trait StringRef extends Expr[ String ] {
      final def append( other: StringRef )( implicit tx: Tx ) : StringRef = StringRef.append( this, other )
   }

   object LongRef {
      implicit def apply( n: Long )( implicit tx: Tx ) : LongRef = new LongConstNew( n, tx )

      def plus( a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongPlus( a, b, tx )
      def min(  a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongMin(  a, b, tx )
      def max(  a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongMax(  a, b, tx )

      private final class LongConstNew( protected val constValue: Long, tx0: Tx )
      extends LongRef with ConstExpr[ Long ] {
         protected val reactor = ReactorBranch[ Confluent ]()( tx0 )
      }

      private final class LongPlus( protected val a: LongRef, protected val b: LongRef, tx0: Tx )
      extends LongRef with BinaryExpr[ Long ] {
         protected val reactor = ReactorBranch[ Confluent ]()( tx0 )
         a.addReactor( reactor )( tx0 )
         b.addReactor( reactor )( tx0 )

         protected def op( ac: Long, bc: Long ) = ac + bc
      }

      private final class LongMin( protected val a: LongRef, protected val b: LongRef, tx0: Tx )
      extends LongRef with BinaryExpr[ Long ] {
         protected val reactor = ReactorBranch[ Confluent ]()( tx0 )
         a.addReactor( reactor )( tx0 )
         b.addReactor( reactor )( tx0 )

         protected def op( ac: Long, bc: Long ) = math.min( ac, bc )
      }

      private final class LongMax( protected val a: LongRef, protected val b: LongRef, tx0: Tx )
      extends LongRef with BinaryExpr[ Long ] {
         protected val reactor = ReactorBranch[ Confluent ]()( tx0 )
         a.addReactor( reactor )( tx0 )
         b.addReactor( reactor )( tx0 )

         protected def op( ac: Long, bc: Long ) = math.max( ac, bc )
      }
   }

   trait LongRef extends Expr[ Long ] {
      final def +(   other: LongRef )( implicit tx: Tx ) : LongRef = LongRef.plus( this, other )
      final def min( other: LongRef )( implicit tx: Tx ) : LongRef = LongRef.min(  this, other )
      final def max( other: LongRef )( implicit tx: Tx ) : LongRef = LongRef.max(  this, other )
   }

//   object Region {
//      def apply( name: StringRef, start: LongRef, stop: LongRef ) : Region = new RegionNew( name, start, stop )
//
//      private final class RegionNew( name0: StringRef, start0: LongRef, stop: LongRef ) extends Region {
//
//      }
//   }

   trait Region {
      def name( implicit tx: Tx ) : StringRef
      def name_=( value: StringRef )( implicit tx: Tx ) : Unit
      def name_# : StringRef

      def start( implicit tx: Tx ) : LongRef
      def start_=( value: LongRef ) : Unit
      def start_# : LongRef

      def stop( implicit tx: Tx ) : LongRef
      def stop_=( value: LongRef ) : Unit
      def stop_# : LongRef
   }

   trait RegionList {
      def head( implicit tx: Tx ) : Option[ List[ Region ]]
      def head_=( r: Option[ List[ Region ]]) : Unit
   }

   trait List[ A ] {
      def value: A
      def next( implicit tx: Tx ) : Option[ List[ A ]]
//      def next_=( elem: Option[ List[ A ]])( implicit tx: Tx ) : Unit
   }

   def run() {
      val system = Confluent()

      val f    = new JFrame( "Reaction Test" )
      val cp   = f.getContentPane



      f.pack()
      f.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      f.setVisible( true )
   }
}
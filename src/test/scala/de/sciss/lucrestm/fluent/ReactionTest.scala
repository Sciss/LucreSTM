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

   trait Expr[ A ] extends Event[ Confluent, A ]

   object StringRef {
      implicit def apply( s: String )( implicit tx: Tx ) : StringRef = new StringConstNew( s, ReactorBranch() )

      def append( a: StringRef, b: StringRef )( implicit tx: Tx ) : StringRef =
         new StringAppendNew( a, b, ReactorBranch() )

      private final class StringConstNew( s: String, protected val reactor: ReactorBranch[ Confluent ])
      extends StringRef {
         def value( implicit tx: Tx ) : String = s
      }

      private final class StringAppendNew( prefix: StringRef, suffix: StringRef,
                                           protected val reactor: ReactorBranch[ Confluent ])
      extends StringRef {
         def value( implicit tx: Tx ) : String = prefix.value + suffix.value
      }
   }

   trait StringRef extends Expr[ String ] {
      final def append( other: StringRef )( implicit tx: Tx ) : StringRef = StringRef.append( this, other )
   }

   object LongRef {
      implicit def apply( n: Long )( implicit tx: Tx ) : LongRef = new LongConstNew( n, ReactorBranch() )

      def plus( a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongPlus( a, b, ReactorBranch() )
      def min(  a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongMin(  a, b, ReactorBranch() )
      def max(  a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongMax(  a, b, ReactorBranch() )

      private final class LongConstNew( n: Long, protected val reactor: ReactorBranch[ Confluent ])
      extends LongRef {
         def value( implicit tx: Tx ) : Long = n
      }

      private final class LongPlus( a: LongRef, b: LongRef, protected val reactor: ReactorBranch[ Confluent ])
      extends LongRef {
         def value( implicit tx: Tx ) : Long = a.value + b.value
      }

      private final class LongMin( a: LongRef, b: LongRef, protected val reactor: ReactorBranch[ Confluent ])
      extends LongRef {
         def value( implicit tx: Tx ) : Long = math.min( a.value, b.value )
      }

      private final class LongMax( a: LongRef, b: LongRef, protected val reactor: ReactorBranch[ Confluent ])
      extends LongRef {
         def value( implicit tx: Tx ) : Long = math.max( a.value, b.value )
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
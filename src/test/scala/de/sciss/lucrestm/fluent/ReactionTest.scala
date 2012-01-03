/*
 *  ReactionTest.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011 Hanns Holger Rutz. All rights reserved.
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
package fluent

import collection.immutable.{IndexedSeq => IIdxSeq}
import annotation.switch
import javax.swing.{JComponent, JTextField, BorderFactory, JLabel, GroupLayout, JPanel, WindowConstants, JFrame}
import java.io.File
import java.awt.event.{WindowAdapter, WindowEvent, ActionListener, ActionEvent}
import java.awt.{BorderLayout, Color, Dimension, Graphics2D, Graphics, GridLayout, EventQueue}

object ReactionTest extends App {
   defer( args.headOption match {
      case Some( "--test2" )     => test2()
      case Some( "--confluent" ) => test1( Confluent() )()
      case Some( "--database" )  =>
         val file = new File( new File( new File( sys.props( "user.home" ), "Desktop" ), "reaction" ), "data" )
         val db   = BerkeleyDB.open( file )
         test1( db )( db.close() )
      case _  => test1( InMemory() )()
   })

   class System[ S <: Sys[ S ]] {
      type Tx  = S#Tx
      type Acc = S#Acc

      type Observer[ A, Repr ]   = State.Observer[ S, A, Repr ]
      type Observable[ A, Repr ] = State.Observable[ S, A, Repr ]
      type Expr[ A ]             = State[ S, A ]
      type ConstExpr[ A ]        = State.Constant[ S, A ]
      type MutableExpr[ A ]      = State.Node[ S, A ]
      type Targets               = State.Targets[ S ]

      trait BinaryExpr[ A ] extends MutableExpr[ A ] {
         protected def a: Expr[ A ]
         protected def b: Expr[ A ]
         protected def op( a: A, b: A ) : A

         final def value( implicit tx: Tx ) : A = op( a.value, b.value )

         final protected def disposeData()( implicit tx: Tx ) {
         }
      }

      object ExprVar {
         sealed trait Impl[ A, Ex <: Expr[ A ]] extends ExprVar[ A, Ex ] {
            me: Ex =>

            protected def reader: State.Reader[ S, Ex ]
            protected implicit def peerSer: TxnSerializer[ Tx, Acc, Ex ]
            protected def v: S#Var[ Ex ]
            protected final def stateSources( implicit tx: Tx ) : State.Sources[ S ] = State.noSources[ S ]

            final def value( implicit tx: Tx ) : A = get.value

            final def get( implicit tx: Tx ) : Ex = v.get

            final def set( ex: Ex )( implicit tx: Tx ) {
               val old = get
               if( old.value != ex.value ) {
                  val conn = targets.isConnected
                  if( conn ) {
                     old.removeReactor( this )
                  }
                  v.set( ex )
                  if( conn ) {
                     ex.addReactor( this )
                     val r = targets.propagate( this, IIdxSeq.empty )
                     r.map( _.apply() ).foreach( _.apply() )
                  }
               }
            }

            final def transform( fun: Ex => Ex )( implicit tx: Tx ) {
   //            v.transform( fun )
               set( fun( get ))
            }

            final protected def writeData( out: DataOutput ) {
               out.writeUnsignedByte( 100 )
               v.write( out )
            }

            final protected def disposeData()( implicit tx: Tx ) {
               v.dispose()
            }

            final def observe( fun: (Tx, A) => Unit )( implicit tx: Tx ) : Observer[ A, Ex ] = {
               val o = State.Observer[ S, A, Ex ]( reader, fun )
               o.add( this )
               fun( tx, value )
               o
            }
         }

         // XXX the other option is to forget about StringRef, LongRef, etc., and instead
         // pimp Expr[ String ] to StringExprOps, etc.
         abstract class New[ A, Ex <: Expr[ A ]]( init: Ex, tx0: Tx )(
            implicit protected val peerSer: TxnSerializer[ Tx, Acc, Ex ])
         extends Impl[ A, Ex ] {
            me: Ex =>

            protected val targets   = State.Targets[ S ]( tx0 )
            protected val v         = tx0.newVar[ Ex ]( id, init )
         }

         abstract class Read[ A, Ex <: Expr[ A ]]( protected val targets: Targets, in: DataInput, tx0: Tx )(
            implicit protected val peerSer: TxnSerializer[ Tx, Acc, Ex ])
         extends Impl[ A, Ex ] {
            me: Ex =>

            protected val v = tx0.readVar[ Ex ]( id, in )
         }
      }
      trait ExprVar[ A, Ex <: /* Mutable */ Expr[ A ]] extends /* Expr[ Ex ] with */ Var[ Tx, Ex ] with State.Node[ S, A ]

      object StringRef {
         implicit def apply( s: String )( implicit tx: Tx ) : StringRef = new StringConstNew( s, tx )

         def append( a: StringRef, b: StringRef )( implicit tx: Tx ) : StringRef =
            new StringAppendNew( a, b, tx )

         private sealed trait StringConst extends StringRef with ConstExpr[ String ] {
            final protected def writeData( out: DataOutput ) {
               out.writeString( constValue )
            }

            final def observe( fun: (Tx, String) => Unit )( implicit tx: Tx ) : Observer[ String, StringRef ] = {
               val o = State.Observer[ S, String, StringRef ]( State.Reader.unsupported[ S, StringRef ], fun )
               fun( tx, value )
               o
            }

            override def toString = constValue
         }

         private final class StringConstNew( protected val constValue: String, tx0: Tx )
         extends StringConst

         private final class StringConstRead( in: DataInput ) extends StringConst {
            protected val constValue: String = in.readString()
         }

         private sealed trait StringBinOp extends StringRef with BinaryExpr[ String ] {
            protected def opID : Int

   //         final protected def reader: State.Reader[ S, StringBinOp ] = sys.error( "TODO" )

            final protected def writeData( out: DataOutput ) {
               out.writeUnsignedByte( opID )
               a.write( out )
               b.write( out )
            }

            final protected def stateSources( implicit t: Tx ) : State.Sources[ S ] = IIdxSeq( a, b )

            final def observe( fun: (Tx, String) => Unit )( implicit tx: Tx ) : Observer[ String, StringRef ] = {
               val o = State.Observer[ S, String, StringRef ]( StringRef.serializer, fun )
               o.add( this )
               fun( tx, value )
               o
            }
         }

         private sealed trait StringAppend {
            me: BinaryExpr[ String ] =>

            final protected def op( ac: String, bc: String ) : String = ac + bc
            final protected def opID = 1

            override def toString = "String.append(" + a + ", " + b + ")"
         }

         private final class StringAppendNew( protected val a: StringRef, protected val b: StringRef, tx0: Tx )
         extends StringBinOp with StringAppend {
            protected val targets = State.Targets[ S ]( tx0 )
         }

         private final class StringAppendRead( protected val targets: Targets, in: DataInput,
                                               access: Acc, tx0: Tx )
         extends StringBinOp with StringAppend {
            protected val a         = serializer.read( in, access )( tx0 )
            protected val b         = serializer.read( in, access )( tx0 )
         }

         implicit val serializer : State.Serializer[ S, StringRef ] =
            new State.Serializer[ S, StringRef ] {
               def readConstant( in: DataInput )( implicit tx: Tx ) : StringRef = new StringConstRead( in )

               def read( in: DataInput, access: Acc, targets: Targets)( implicit tx: Tx ) : StringRef = {
                  (in.readUnsignedByte(): @switch) match {
                     case 1   => new StringAppendRead( targets, in, access, tx )
                     case 100 => new ExprVar.Read[ String, StringRef ]( targets, in, tx ) with StringRef {
                        override def toString = "String.ref(" + v + ")"
                     }
                  }
               }
            }
      }

      trait StringRef extends Expr[ String ] with Observable[ String, StringRef ] {
         final def append( other: StringRef )( implicit tx: Tx ) : StringRef = StringRef.append( this, other )
         final protected def reader: State.Reader[ S, StringRef ] = StringRef.serializer
      }

      object LongRef {
         implicit def apply( n: Long )( implicit tx: Tx ) : LongRef = new LongConstNew( n, tx )

         def plus( a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongPlusNew( a, b, tx )
         def min(  a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongMinNew(  a, b, tx )
         def max(  a: LongRef, b: LongRef )( implicit tx: Tx ) : LongRef = new LongMaxNew(  a, b, tx )

         private sealed trait LongConst extends LongRef with ConstExpr[ Long ] {
            final protected def writeData( out: DataOutput ) {
               out.writeLong( constValue )
            }

            final def observe( fun: (Tx, Long) => Unit )( implicit tx: Tx ) : Observer[ Long, LongRef ] = {
               val o = State.Observer[ S, Long, LongRef ]( State.Reader.unsupported[ S, LongRef ], fun )
               fun( tx, value )
               o
            }
         }

         private final class LongConstNew( protected val constValue: Long, tx0: Tx )
         extends LongConst

         private final class LongConstRead( in: DataInput )
         extends LongConst {
            protected val constValue: Long = in.readLong()
         }

         private sealed trait LongBinOp extends LongRef with BinaryExpr[ Long ] {
            protected def opID: Int

            final protected def stateSources( implicit tx: Tx ) : State.Sources[ S ] = IIdxSeq( a, b )

            final protected def writeData( out: DataOutput ) {
               out.writeUnsignedByte( opID )
               a.write( out )
               b.write( out )
            }

            final def observe( fun: (Tx, Long) => Unit )( implicit tx: Tx ) : Observer[ Long, LongRef ] = {
               val o = State.Observer[ S, Long, LongRef ]( LongRef.serializer, fun )
               o.add( this )
               fun( tx, value )
               o
            }
         }

         private abstract class LongBinOpNew( tx0: Tx ) extends LongBinOp {
            protected val targets = State.Targets[ S ]( tx0 )
         }

         private abstract class LongBinOpRead( in: DataInput, access: Acc, tx0: Tx )
         extends LongBinOp {
            final protected val a   = serializer.read( in, access )( tx0 )
            final protected val b   = serializer.read( in, access )( tx0 )
         }

         private sealed trait LongPlus {
            final protected def op( ac: Long, bc: Long ) = ac + bc
            final protected def opID = 1
         }

         private sealed trait LongMin {
            final protected def op( ac: Long, bc: Long ) = math.min( ac, bc )
            final protected def opID = 2
         }

         private sealed trait LongMax {
            final protected def op( ac: Long, bc: Long ) = math.max( ac, bc )
            final protected def opID = 3
         }

         private final class LongPlusNew( protected val a: LongRef, protected val b: LongRef, tx0: Tx )
         extends LongBinOpNew( tx0 ) with LongPlus

         private final class LongPlusRead( protected val targets: Targets, in: DataInput, access: Acc, tx0: Tx )
         extends LongBinOpRead( in, access, tx0 ) with LongPlus

         private final class LongMinNew( protected val a: LongRef, protected val b: LongRef, tx0: Tx )
         extends LongBinOpNew( tx0 ) with LongMin

         private final class LongMinRead( protected val targets: Targets, in: DataInput, access: Acc, tx0: Tx )
         extends LongBinOpRead( in, access, tx0 ) with LongMin

         private final class LongMaxNew( protected val a: LongRef, protected val b: LongRef, tx0: Tx )
         extends LongBinOpNew( tx0 ) with LongMax

         private final class LongMaxRead( protected val targets: Targets, in: DataInput, access: Acc, tx0: Tx )
         extends LongBinOpRead( in, access, tx0 ) with LongMax

         implicit val serializer : State.Serializer[ S, LongRef ] =
            new State.Serializer[ S, LongRef ] {
               def readConstant( in: DataInput )( implicit tx: Tx ) : LongRef = new LongConstRead( in )

               def read( in: DataInput, access: Acc, targets: Targets )( implicit tx: Tx ) : LongRef = {
                  val opID    = in.readUnsignedByte()
                  (opID: @switch) match {
                     case 1   => new LongPlusRead( targets, in, access, tx )
                     case 2   => new LongMinRead( targets, in, access, tx )
                     case 3   => new LongMaxRead( targets, in, access, tx )
                     case 100 => new ExprVar.Read[ Long, LongRef ]( targets, in, tx ) with LongRef
                  }
               }
            }
      }

      trait LongRef extends Expr[ Long ] with Observable[ Long, LongRef ] {
         final def +(   other: LongRef )( implicit tx: Tx ) : LongRef = LongRef.plus( this, other )
         final def min( other: LongRef )( implicit tx: Tx ) : LongRef = LongRef.min(  this, other )
         final def max( other: LongRef )( implicit tx: Tx ) : LongRef = LongRef.max(  this, other )
         final protected def reader: State.Reader[ S, LongRef ] = LongRef.serializer
      }

   //   object Region {
   //      def apply( name: StringRef, start: LongRef, stop: LongRef ) : Region = new RegionNew( name, start, stop )
   //
   //      private final class RegionNew( name0: StringRef, start0: LongRef, stop: LongRef ) extends Region {
   //
   //      }
   //   }

      object Region {
         def apply( name: StringRef, start: LongRef, stop: LongRef )
                  ( implicit tx: Tx ) : Region =
            new RegionNew( name, start, stop, tx )

         private final class RegionNew( name0: StringRef, start0: LongRef, stop0: LongRef, tx0: Tx )
         extends Region {
            region =>

            val id = tx0.newID()

            val name_# = new ExprVar.New[ String, StringRef ]( name0, tx0 ) with StringRef {
               override def toString = region.toString + ".name_#"
            }
            def name( implicit tx: Tx ) : StringRef = name_#.get
            def name_=( value: StringRef )( implicit tx: Tx ) { name_#.set( value )}

            val start_# = new ExprVar.New[ Long, LongRef ]( start0, tx0 ) with LongRef
            def start( implicit tx: Tx ) : LongRef = start_#.get
            def start_=( value: LongRef )( implicit tx: Tx ) { start_#.set( value )}

            val stop_# = new ExprVar.New[ Long, LongRef ]( stop0, tx0 ) with LongRef
            def stop( implicit tx: Tx ) : LongRef = stop_#.get
            def stop_=( value: LongRef )( implicit tx: Tx ) { stop_#.set( value )}

            override def toString = "Region" + id
         }
      }

      trait Region {
         def name( implicit tx: Tx ) : StringRef
         def name_=( value: StringRef )( implicit tx: Tx ) : Unit
         def name_# : StringRef

         def start( implicit tx: Tx ) : LongRef
         def start_=( value: LongRef )( implicit tx: Tx ) : Unit
         def start_# : LongRef

         def stop( implicit tx: Tx ) : LongRef
         def stop_=( value: LongRef )( implicit tx: Tx ) : Unit
         def stop_# : LongRef
      }

   //   object RegionList {
   //      def empty( implicit tx: Tx ) : RegionList = new Impl( tx )
   //
   //      private final class Impl( tx0: Tx ) extends RegionList {
   ////         protected val reactor = StateNode[ S ]( StateSources.none )( tx0 )
   //         def value( implicit tx: Tx ): IIdxSeq[ Change ] = sys.error( "TODO" )
   //         def write( out: DataOutput ) { sys.error( "TODO" )}
   //         def dispose()( implicit tx: Tx ) { sys.error( "TODO" )}
   //      }
   //
   //      sealed trait Change
   //      final case class Added( region: Region )
   //      final case class Removed( region: Region )
   //   }
   //
   //   trait RegionList extends Expr[ IIdxSeq[ RegionList.Change ], RegionList ] {
   ////      def head( implicit tx: Tx ) : Option[ List[ Region ]]
   ////      def head_=( r: Option[ List[ Region ]]) : Unit
   ////      def tail( implicit tx: Tx ) : Option[ List[ Region ]]
   ////      def tail_=( r: Option[ List[ Region ]]) : Unit
   //   }
   //
   //   trait List[ A ] {
   //      def value: A
   //      def next( implicit tx: Tx ) : Option[ List[ A ]]
   ////      def next_=( elem: Option[ List[ A ]])( implicit tx: Tx ) : Unit
   //   }

      final class RegionView( r: Region, id: String ) extends JPanel {
         private val lay = new GroupLayout( this )
         lay.setAutoCreateContainerGaps( true )
         setLayout( lay )
         setBorder( BorderFactory.createTitledBorder( BorderFactory.createEtchedBorder(), id ))

         private val lbName   = new JLabel( "Name:" )
         private val lbStart  = new JLabel( "Start:" )
         private val lbStop   = new JLabel( "Stop:" )

         private val ggName   = new JTextField( 12 )
         private val ggStart  = new JTextField( 8 )
         private val ggStop   = new JTextField( 8 )

         lay.setHorizontalGroup( lay.createSequentialGroup()
            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.TRAILING )
               .addComponent( lbName )
               .addComponent( lbStart )
               .addComponent( lbStop )
            )
            .addGroup( lay.createParallelGroup()
               .addComponent( ggName )
               .addComponent( ggStart )
               .addComponent( ggStop )
            )
         )

         lay.setVerticalGroup( lay.createSequentialGroup()
            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.BASELINE )
               .addComponent( lbName )
               .addComponent( ggName )
            )
            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.BASELINE )
               .addComponent( lbStart )
               .addComponent( ggStart )
            )
            .addGroup( lay.createParallelGroup( GroupLayout.Alignment.BASELINE )
               .addComponent( lbStop )
               .addComponent( ggStop )
            )
         )

         private def stringToModel( s: String, model: (Tx, String) => Unit )( implicit system: S ) {
            system.atomic { implicit tx =>
               model( tx, s )
            }
         }

         private def longToModel( n: Long, model: (Tx, Long) => Unit )( implicit system: S ) {
            system.atomic { implicit tx => model( tx, n )}
         }

         def connect()( implicit tx: Tx ) {
            r.name_#.observe(  (_, v) => defer( ggName.setText(  v )))
            r.start_#.observe( (_, v) => defer( ggStart.setText( v.toString )))
            r.stop_#.observe(  (_, v) => defer( ggStop.setText(  v.toString )))

            implicit val system = tx.system

            ggName.addActionListener( new ActionListener {
               def actionPerformed( e: ActionEvent ) {
                  stringToModel( ggName.getText, (tx, s) => {
                     implicit val _tx = tx
                     r.name = s
                  })
               }
            })

            ggStart.addActionListener( new ActionListener {
               def actionPerformed( e: ActionEvent ) {
                  longToModel( ggStart.getText.toLong, (tx, n) => { implicit val _tx = tx; r.start = n })
               }
            })

            ggStop.addActionListener( new ActionListener {
               def actionPerformed( e: ActionEvent ) {
                  longToModel( ggStop.getText.toLong, (tx, n) => { implicit val _tx = tx; r.stop = n })
               }
            })
         }
      }
   }

   def defer( thunk: => Unit ) { EventQueue.invokeLater( new Runnable { def run() { thunk }})}

   def test1[ S <: Sys[ S ]]( system: S )( cleanUp: => Unit ) {
      val infra = new System[ S ]
      import infra._

      val f    = new JFrame( "Reaction Test" )
      val cp   = f.getContentPane

      cp.setLayout( new GridLayout( 3, 1 ))
      val rs = system.atomic { implicit tx =>
         val _r1   = Region( "eins", 0L, 10000L )
         val _r2   = Region( "zwei", 5000L, 12000L )
         val _r3   = Region( _r1.name_#.append( "+" ).append( _r2.name_# ),
                             _r1.start_#.min( _r2.start_# ).+( -100L ),
                             _r1.stop_#.max( _r2.stop_# ).+( 100L ))
         Seq( _r1, _r2, _r3 )
      }

      val vs = rs.zipWithIndex.map {
         case (r, i) => new RegionView( r, "Region #" + (i+1) )
      }

      system.atomic { implicit tx =>
         vs.foreach( _.connect() )
      }

      vs.foreach( cp.add )

      f.setResizable( false )
      f.pack()
      f.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE )
      f.setLocationRelativeTo( null )
      f.setVisible( true )

      f.addWindowListener( new WindowAdapter {
         override def windowClosing( e: WindowEvent ) {
            f.dispose()
            try {
               cleanUp
            } finally {
               sys.exit( 0 )
            }
         }
      })
   }

   class TrackView extends JComponent {
      setPreferredSize( new Dimension( 800, 600 ))

      private var cycle = 0.0f

      override def paintComponent( g: Graphics ) {
         val g2 = g.asInstanceOf[ Graphics2D ]
         g2.setColor( Color.getHSBColor( cycle, 1f, 1f ))
         cycle = (cycle + 0.1f) % 1.0f
         val w = getWidth
         val h = getHeight
         g2.fillRect( 0, 0, w, h )  // show last damaged regions
      }
   }

   def test2() {
      val f    = new JFrame( "Reaction Test 2" )
      val cp   = f.getContentPane
      val tr   = new TrackView
      cp.add( tr, BorderLayout.CENTER )
      f.setResizable( false )
      f.pack()
      f.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      f.setLocationRelativeTo( null )
      f.setVisible( true )
   }
}
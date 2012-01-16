/*
*  ReactionTest2.scala
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

package de.sciss.lucre
package expr

import collection.immutable.{IndexedSeq => IIdxSeq}
import java.io.File
import java.awt.event.{WindowAdapter, WindowEvent, ActionListener, ActionEvent}
import java.awt.{BorderLayout, Color, Dimension, Graphics2D, Graphics, GridLayout, EventQueue}
import javax.swing.{AbstractAction, JButton, Box, JComponent, JTextField, BorderFactory, JLabel, GroupLayout, JPanel, WindowConstants, JFrame}
import annotation.{tailrec, switch}
import collection.mutable.Buffer
import stm.{TxnSerializer, Sys}
import stm.impl.{InMemory, Confluent, BerkeleyDB}
import stm.Mutable
import event.{Compound, Decl, Event, LateBinding, Root, Source, Observer, EarlyBinding, Invariant}

object ReactionTest2 extends App {
   private def memorySys    : (InMemory, () => Unit) = (InMemory(), () => ())
   private def confluentSys : (Confluent, () => Unit) = (Confluent(), () => ())
   private def databaseSys  : (BerkeleyDB, () => Unit) = {
      val file = new File( new File( new File( sys.props( "user.home" ), "Desktop" ), "reaction" ), "data" )
      val db   = BerkeleyDB.open( file )
      (db, () => db.close())
   }

   defer( args.toSeq.take( 2 ) match {
      case Seq( "--coll-memory" )      => collections( memorySys )
      case Seq( "--coll-confluent" )   => collections( confluentSys )
      case Seq( "--coll-database" )    => collections( databaseSys )
      case Seq( "--expr-memory" )      => expressions( memorySys )
      case Seq( "--expr-confluent" )   => expressions( confluentSys )
      case Seq( "--expr-database" )    => expressions( databaseSys )
      case _  => println( """
Usages:
   --coll-memory
   --coll-confluent
   --coll-database

   --expr-memory
   --expr-confluent
   --expr-database
""" )
   })

   object System {
      def apply[ S <: Sys[ S ]]( implicit tx: S#Tx ) : System[ S ] = {
         val strings = Strings[ S ]
         val longs   = Longs[ S ]
         val spans   = Spans[ S ]( longs )
         new System[ S ]( strings, longs, spans )
      }
   }

   class System[ S <: Sys[ S ]] private( val strings: Strings[ S ], val longs: Longs[ S ], val spans: Spans[ S ]) {
      type Tx  = S#Tx
      type Acc = S#Acc

//      import string.{ops => stringOps, Const => stringConst}
//      import long.{ops => longOps, Const => longConst}
      import strings.{stringOps, Ex => StringEx}
      import longs.{longOps, Ex => LongEx}
      import spans.{spanOps, Ex => SpanEx}
      implicit def stringConst( s: String ) : StringEx = strings.Const( s )   // why doesn't the direct import work??
      implicit def longConst( n: Long ) : LongEx = longs.Const( n )   // why doesn't the direct import work??
      implicit def spanConst( span: Span ) : SpanEx = spans.Const( span )   // why doesn't the direct import work??

   //   object Region {
   //      def apply( name: StringEx, start: LongEx, stop: LongEx ) : Region = new RegionNew( name, start, stop )
   //
   //      private final class RegionNew( name0: StringEx, start0: LongEx, stop: LongEx ) extends Region {
   //
   //      }
   //   }

      object Region {
         def apply( name: StringEx, span: SpanEx )( implicit tx: Tx ) : Region = new New( name, span, tx )

         private final class New( name0: StringEx, span0: SpanEx, tx0: Tx )
         extends RegionLike.Impl with Region {
            region =>

            val id      = tx0.newID()
            val name_#  = strings.NamedVar( region.toString + ".name_#", name0 )( tx0 )
            val span_#  = spans.NamedVar(   region.toString + ".span_#", span0 )( tx0 )
         }

         private final class Read( in: DataInput, acc: S#Acc, tx0: S#Tx ) extends RegionLike.Impl with Region {
            region =>

            val id      = tx0.readID( in, acc )
            val name_#  = strings.readVar( in, acc )( tx0 )
            val span_#  = spans.readVar(   in, acc )( tx0 )
         }

         implicit val serializer : TxnSerializer[ S#Tx, S#Acc, Region ] = new TxnSerializer[ S#Tx, S#Acc, Region ] {
            def write( v: Region, out: DataOutput ) { v.write( out )}
            def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : Region =
               new Read( in, access, tx )
         }
      }

      object RegionLike {
         sealed trait Impl extends RegionLike {
            def name_# : strings.Var
//            def start_# : longs.Var
//            def stop_# : longs.Var
            def span_# : spans.Var

            final def name( implicit tx: Tx ) : StringEx = name_#.get
            final def name_=( value: StringEx )( implicit tx: Tx ) { name_#.set( value )}

//            final def start( implicit tx: Tx ) : LongEx = start_#.get
//            final def start_=( value: LongEx )( implicit tx: Tx ) { start_#.set( value )}
//
//            final def stop( implicit tx: Tx ) : LongEx = stop_#.get
//            final def stop_=( value: LongEx )( implicit tx: Tx ) { stop_#.set( value )}

            final def span( implicit tx: Tx ) : SpanEx = span_#.get
            final def span_=( value: SpanEx )( implicit tx: Tx ) { span_#.set( value )}

            final protected def writeData( out: DataOutput ) {
               name_#.write( out )
               span_#.write( out )
            }

            final protected def disposeData()( implicit tx: S#Tx ) {
               name_#.dispose()
               span_#.dispose()
            }
         }
      }

      trait RegionLike {
         def name( implicit tx: Tx ) : StringEx
         def name_=( value: StringEx )( implicit tx: Tx ) : Unit
         def name_# : StringEx

//         def start( implicit tx: Tx ) : LongEx
//         def start_=( value: LongEx )( implicit tx: Tx ) : Unit
//         def start_# : LongEx
//
//         def stop( implicit tx: Tx ) : LongEx
//         def stop_=( value: LongEx )( implicit tx: Tx ) : Unit
//         def stop_# : LongEx

         def span( implicit tx: Tx ) : SpanEx
         def span_=( value: SpanEx )( implicit tx: Tx ) : Unit
         def span_# : SpanEx
      }

      trait Region extends RegionLike with Mutable[ S ] {
         override def toString = "Region" + id
      }

      object EventRegion extends Decl[ S, EventRegion ] {
         sealed trait ChangedLike extends Update { def r: EventRegion }
         final case class Changed( r: EventRegion ) extends ChangedLike
         final case class Renamed( r: EventRegion, change: event.Change[ String ]) extends ChangedLike
         final case class Moved(   r: EventRegion, change: event.Change[ Span ]) extends ChangedLike

         declare[ Renamed ]( _.renamed )
         declare[ Moved   ]( _.moved   )
         declare[ Changed ]( _.changed )

         def apply( name: StringEx, span: SpanEx )
                  ( implicit tx: Tx ) : EventRegion = new New( name, span, tx )

         private sealed trait Impl extends RegionLike.Impl with EventRegion {
            final lazy val renamed  = name_#.changed.map( Renamed( this, _ ))
            final lazy val moved    = span_#.changed.map( Moved( this, _ ))
            final lazy val changed  = (renamed | moved).map( ch => Changed( ch.r ))

            final protected def sources( implicit tx: S#Tx ) = IIdxSeq( name_#, span_# )
            final protected def reader = serializer
            final protected def decl   = EventRegion
         }

         private final class New( name0: StringEx, span0: SpanEx, tx0: S#Tx ) extends Impl {
            region =>

            protected val targets = Invariant.Targets[ S ]( tx0 )
            val name_#  = strings.NamedVar( region.toString + ".name_#",  name0 )(  tx0 )
            val span_#  = spans.NamedVar(   region.toString + ".span_#",  span0 )(  tx0 )
         }

         private final class Read( in: DataInput, access: S#Acc,
                                   protected val targets: Invariant.Targets[ S ], tx0: S#Tx ) extends Impl {
            val name_#  = strings.readVar( in, access )( tx0 )
            val span_#  = spans.readVar(   in, access )( tx0 )
         }

         implicit val serializer : Invariant.Serializer[ S, EventRegion ] = new Invariant.Serializer[ S, EventRegion ] {
//            def write( v: EventRegion, out: DataOutput ) { v.write( out )}
            def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : EventRegion =
               new Read( in, access, targets, tx )
         }
      }
      trait EventRegion extends RegionLike with Invariant[ S, EventRegion.Update ] with LateBinding[ S, EventRegion.Update ]
      with Compound[ S, EventRegion, EventRegion.type ] {
         import EventRegion._

         def renamed: Event[ S, Renamed, EventRegion ]
         def moved:   Event[ S, Moved,   EventRegion ]
         def changed: Event[ S, Changed, EventRegion ]
//         final def renamed = name_#.changed.map( Renamed( this, _ ))
//         final def moved   = span_#.changed.map( Moved(   this, _ ))
      }

      object RegionList extends Decl[ S, RegionList ] {
//         sealed trait Change
         final case class Added( l: RegionList, idx: Int, region: EventRegion ) extends Update
         final case class Removed( l: RegionList, idx: Int, region: EventRegion ) extends Update
//         final case class Renamed( region: Region, change: event.Change[ String ]) extends Update

         declare[ Update ]( _.changed )

         def empty( implicit tx: Tx ) : RegionList = new New( tx )

         private sealed trait Impl extends RegionList {
            private type LO = Option[ LinkedList[ EventRegion ]]

            final lazy val changed = event[ RegionList.Update ]

            final protected def decl = RegionList

            protected def sizeRef: S#Var[ Int ]
            protected def headRef: S#Var[ LO ]
//            protected def regionRenamed: RegionRenamed

            final protected def writeData( out: DataOutput ) {
               sizeRef.write( out )
               headRef.write( out )
            }

            final protected def disposeData()( implicit tx: S#Tx ) {
               sizeRef.dispose()
               headRef.dispose()
            }

            final def size( implicit tx: S#Tx ) : Int = sizeRef.get

            final def insert( idx: Int, r: EventRegion )( implicit tx: S#Tx ) {
               if( idx < 0 ) throw new IllegalArgumentException( idx.toString )
               @tailrec def step( i: Int, pred: S#Var[ LO ]) {
                  if( i == idx ) insert( pred, r, idx ) else pred.get match {
                     case None => throw new IndexOutOfBoundsException( idx.toString )
                     case Some( l ) => step( i + 1, l.next_# )
                  }
               }
               step( 0, headRef )
            }

            private def insert( pred: S#Var[ LO ], r: EventRegion, idx: Int )( implicit tx: S#Tx ) {
               val l = LinkedList[ EventRegion ]( r, pred.get: Option[ LinkedList[ EventRegion ]])
               pred.set( Some( l ))
               sizeRef.transform( _ + 1 )
//               r.name_#.addReactor( regionRenamed )
               changed( RegionList.Added( this, idx, r ))
            }

            final def removeAt( idx: Int )( implicit tx: S#Tx ) {
               if( idx < 0 ) throw new IllegalArgumentException( idx.toString )
               @tailrec def step( i: Int, pred: S#Var[ LO ]) {
                  pred.get match {
                     case None => throw new IndexOutOfBoundsException( idx.toString )
                     case Some( l ) =>
                        if( i == idx ) remove( pred, l, idx )
                        else step( i + 1, l.next_# )
                  }
               }
               step( 0, headRef )
            }

            final def apply( idx: Int )( implicit tx: S#Tx ) : EventRegion = {
               if( idx < 0 ) throw new IllegalArgumentException( idx.toString )
               @tailrec def step( i: Int, pred: S#Var[ LO ]) : EventRegion = {
                  pred.get match {
                     case None => throw new IndexOutOfBoundsException( idx.toString )
                     case Some( l ) =>
                        if( i == idx ) l.value
                        else step( i + 1, l.next_# )
                  }
               }
               step( 0, headRef )
            }

            final def remove( r: EventRegion )( implicit tx: S#Tx ) : Boolean = {
               @tailrec def step( i: Int, pred: S#Var[ LO ]) : Boolean = {
                  pred.get match {
                     case None => false
                     case Some( l ) =>
                        if( l == r ) {
                           remove( pred, l, i )
                           true
                        }
                        else step( i + 1, l.next_# )
                  }
               }
               step( 0, headRef )
            }

            private def remove( pred: S#Var[ LO ], r: LinkedList[ EventRegion ], idx: Int )( implicit tx: S#Tx ) {
               pred.set( r.next )
               sizeRef.transform( _ - 1 )
               changed( RegionList.Removed( this, idx, r.value ))
            }

            final def indexOf( r: EventRegion )( implicit tx: S#Tx ) : Int = {
               @tailrec def step( i: Int, pred: S#Var[ LO ]) : Int = {
                  pred.get match {
                     case None => -1
                     case Some( l ) =>
                        if( l == r ) i else step( i + 1, l.next_# )
                  }
               }
               step( 0, headRef )
            }
         }

         private final class New( tx0: Tx ) extends Impl {
            protected val targets         = Invariant.Targets[ S ]( tx0 )
            protected val sizeRef         = tx0.newIntVar( id, 0 )
            protected val headRef         = tx0.newVar[ Option[ LinkedList[ EventRegion ]]]( id, None )
//            protected val regionRenamed   = new RegionRenamed {
//               protected val targets      = Event.Invariant.Targets[ S ]( tx0 )
//            }
         }

         private final class Read( in: DataInput, access: S#Acc, protected val targets: Invariant.Targets[ S ], tx0: S#Tx )
         extends Impl {
            protected val sizeRef   = tx0.readIntVar( id, in )
            protected val headRef   = tx0.readVar[ Option[ LinkedList[ EventRegion ]]]( id, in )
         }

         implicit val serializer : Invariant.Serializer[ S, RegionList ] = new Invariant.Serializer[ S, RegionList ] {
            def read( in: DataInput, access: S#Acc, targets: Invariant.Targets[ S ])( implicit tx: S#Tx ) : RegionList =
               new Read( in, access, targets, tx )
         }
      }

      trait RegionList extends Invariant[ S, RegionList.Update ] with EarlyBinding[ S, RegionList.Update ]
      with Compound[ S, RegionList, RegionList.type ] {
         def size( implicit tx: S#Tx ) : Int
         def insert( idx: Int, r: EventRegion )( implicit tx: S#Tx ) : Unit
         final def add( r: EventRegion )( implicit tx: S#Tx ) { insert( size, r )}
         def removeAt( idx: Int )( implicit tx: S#Tx ) : Unit
         def indexOf( r: EventRegion )( implicit tx: S#Tx ) : Int
         def remove( r: EventRegion )( implicit tx: S#Tx ) : Boolean
         def apply( idx: Int )( implicit tx: S#Tx ) : EventRegion

         def changed: Event[ S, RegionList.Update, RegionList ]

//         final def observe( fun: (S#Tx, RegionList.Change) => Unit )( implicit tx: S#Tx ) : Observer[ S, RegionList.Change, RegionList ] = {
//            val o = Observer[ S, RegionList.Change, RegionList ]( RegionList.serializer, fun )
//            o.add( this )
//            o
//         }
      }

      object LinkedList {
         def apply[ A ]( value: A, next: Option[ LinkedList[ A ]])( implicit tx: S#Tx, peerSer: TxnSerializer[ S#Tx, S#Acc, A ]) : LinkedList[ A ] =
            new New[ A ]( value, next, tx, peerSer )

         private sealed trait Impl[ A ] extends LinkedList[ A ] {
            protected def peerSer: TxnSerializer[ S#Tx, S#Acc, A ]
            final protected def writeData( out: DataOutput ) {
               peerSer.write( value, out )
               next_#.write( out )
            }

            final protected def disposeData()( implicit tx: S#Tx ) {
               next_#.dispose()
            }
         }

         private final class New[ A ]( val value: A, next0: Option[ LinkedList[ A ]], tx0: S#Tx,
                                       protected implicit val peerSer: TxnSerializer[ S#Tx, S#Acc, A ]) extends Impl[ A ] {
            val id      = tx0.newID()
            val next_#  = tx0.newVar[ Option[ LinkedList[ A ]]]( id, next0 )
         }

         private final class Read[ A ]( in: DataInput, acc: S#Acc, tx0: S#Tx,
                                        protected implicit val peerSer: TxnSerializer[ S#Tx, S#Acc, A ] ) extends Impl[ A ] {
            val id      = tx0.readID( in, acc )
            val value   = peerSer.read( in, acc )( tx0 )
            val next_#  = tx0.readVar[ Option[ LinkedList[ A ]]]( id, in )
         }

         implicit def serializer[ A ]( implicit peerSer: TxnSerializer[ S#Tx, S#Acc, A ]) : TxnSerializer[ S#Tx, S#Acc, LinkedList[ A ]] =
            new TxnSerializer[ S#Tx, S#Acc, LinkedList[ A ]] {
               def write( v: LinkedList[ A ], out: DataOutput ) { v.write( out )}
               def read( in: DataInput, access: S#Acc )( implicit tx: S#Tx ) : LinkedList[ A ] =
                  new Read[ A ]( in, access, tx, peerSer )
            }
      }

      trait LinkedList[ A ] extends Mutable[ S ] {
         def value: A
         def next_# : S#Var[ Option[ LinkedList[ A ]]]
         final def next( implicit tx: Tx ) : Option[ LinkedList[ A ]] = next_#.get
         final def next_=( elem: Option[ LinkedList[ A ]])( implicit tx: Tx ) { next_#.set( elem )}
      }

      final class RegionView[ R <: RegionLike ]( rv: S#Var[ R ], id: String ) extends JPanel {
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

         private def stringToModel( s: String, model: (Tx, R, String) => Unit )( implicit system: S ) {
//            system.atomic { implicit tx =>
//               model( tx, s )
//            }
            system.atomic { tx =>
               model( tx, tx.access( rv ), s )
            }
         }

         private def longToModel( n: Long, model: (Tx, Long) => Unit )( implicit system: S ) {
            system.atomic { implicit tx => model( tx, n )}
         }

         def connect()( implicit tx: Tx ) {
            connect( tx.access( rv ))
         }

         private def connect( r: R )( implicit tx: Tx ) {
//            r.name_#.changed.react  { case (_, event.Change( _, v )) => defer( ggName.setText(  v ))}
            // new way -- simpler observer
            r.name_#.observe { (_, v) => defer( ggName.setText(  v ))}
            // old full way -- observe changed events
//            r.start_#.changed.react { case (_, event.Change( _, v )) => defer( ggStart.setText( v.toString ))}
//            r.stop_#.changed.react  { case (_, event.Change( _, v )) => defer( ggStop.setText(  v.toString ))}

            r.span_#.observe( (tx, newSpan) => defer {
               ggStart.setText( newSpan.start.toString )
               ggStop.setText( newSpan.stop.toString )
            })

//            val name0   = r.name.value
//            val start0  = r.start.value
//            val stop0   = r.stop.value

//            defer {
////               ggName.setText( name0 )
//               ggStart.setText( start0.toString )
//               ggStop.setText( stop0.toString )
//            }

            implicit val system = tx.system

            ggName.addActionListener( new ActionListener {
               def actionPerformed( e: ActionEvent ) {
                  stringToModel( ggName.getText, (tx, r, s) => {
                     implicit val _tx = tx
                     r.name = s
                  })
               }
            })

            ggStart.addActionListener( new ActionListener {
               def actionPerformed( e: ActionEvent ) {
                  longToModel( ggStart.getText.toLong, (tx, n) => { implicit val _tx = tx
//                     r.start = n
                     r.span = spans.Span( n, r.span.stop )
                  })
               }
            })

            ggStop.addActionListener( new ActionListener {
               def actionPerformed( e: ActionEvent ) {
                  longToModel( ggStop.getText.toLong, (tx, n) => { implicit val _tx = tx
//                     r.stop = n
                     r.span = spans.Span( r.span.start, n )
                  })
               }
            })
         }
      }
   }

   def defer( thunk: => Unit ) { EventQueue.invokeLater( new Runnable { def run() { thunk }})}

   def expressions[ S <: Sys[ S ]]( tup: (S, () => Unit) ) {
      val (system, cleanUp) = tup
      val (infra, vs, r3v) = system.atomic { implicit tx =>
         val _infra = System[ S ]
         import _infra._
         import strings.stringOps
         import longs.longOps
         import spans.spanOps

         val _r1   = EventRegion( "eins", Span( 0L, 10000L ))
         val _r2   = EventRegion( "zwei", Span( 5000L, 12000L ))
         val _r3   = EventRegion( _r1.name_#.append( "+" ).append( _r2.name_# ),
//            longOps( _r1.start_#.min( _r2.start_# )).+( -100L ),
//            longOps( _r1.stop_#.max( _r2.stop_# )).+( 100L ))
            spans.Span(
               longOps( _r1.span_#.start.min( _r2.span_#.start)).+( -100L ),
               longOps( _r1.span_#.stop.max(  _r2.span_#.stop)).+(   100L ))
            )
         val rootID  = tx.newID()
         val _rvs    = Seq( _r1, _r2, _r3 ).map( tx.newVar( rootID, _ ))

         val _vs = _rvs.zipWithIndex.map {
   //         case (r, i) => new RegionView( r, "Region #" + (i+1) )
            case (rv, i) => new RegionView[ EventRegion ]( rv, "Region #" + (i+1) )
         }

         (_infra, _vs, _rvs.last)
      }

      import infra._
      import event.Change

      val f    = frame( "Reaction Test", cleanUp )
      val cp   = f.getContentPane

      cp.setLayout( new GridLayout( 3, 1 ))

      system.atomic { implicit tx =>
         vs.foreach( _.connect() )
         val _r3 = tx.access( r3v )
//         _r3.renamed.react { case (_, EventRegion.Renamed( _, Change( _, newName ))) =>
//            println( "Renamed to '" + newName + "'" )
//         }
         _r3.changed.react { (_, _) =>
//            println( "Renamed to '" + newName + "'" )
            println( "Changed" )
         }
      }

      vs.foreach( cp.add )

      showFrame( f )
   }

   class TrackItem( name0: String, span0: Span ) {
      var name: String  = name0
      var span: Span    = span0
   }

   class TrackView extends JComponent {
      private val items = Buffer.empty[ TrackItem ]
      private val colrRegion = new Color( 0x00, 0x00, 0x00, 0x80 )

      var start         = 0L
      var stop          = 44100L * 20
      var regionHeight  = 32

      setPreferredSize( new Dimension( 800, 600 ))

      private var cycle = 0.0f

      def insert( idx: Int, r: TrackItem ) {
         items.insert( idx, r )
         if( idx == items.size - 1 ) {
            repaintTracks( r.span.start, r.span.stop, idx, idx + 1 )
         } else {
            repaintTracks( start, stop, idx, items.size )
         }
      }

      def removeAt( idx: Int ) {
         val it = items.remove( idx )
         if( idx == items.size ) {
            repaintTracks( it.span.start, it.span.stop, idx, idx + 1 )
         } else {
            repaintTracks( start, stop, idx, items.size + 1 )
         }
      }

      private def trackHeight = regionHeight + 2

      private def repaintTracks( rstart: Long, rstop: Long, ystart: Int, ystop: Int ) {
         val w          = getWidth
         val scale      = w.toDouble / (stop - start)
         val rx1        = (rstart * scale).toInt
         val rx2        = (rstop * scale).toInt
         val ry1        = ystart * trackHeight
         val ry2        = ystop * trackHeight
         repaint( rx1, ry1, (rx2 - rx1), (ry2 - ry1) )
      }

      override def paintComponent( g: Graphics ) {
         val g2 = g.asInstanceOf[ Graphics2D ]
         g2.setColor( Color.getHSBColor( cycle, 1f, 1f ))
         cycle = (cycle + 0.1f) % 1.0f
         val w = getWidth
         val h = getHeight
         g2.fillRect( 0, 0, w, h )  // show last damaged regions

         val scale      = w.toDouble / (stop - start)
         val cr         = g2.getClipBounds
         val clipOrig   = g2.getClip
         val fm         = g2.getFontMetrics

         items.foldLeft( 0 ) { (y, it) =>
            if( y < (cr.y + cr.height) && (y + regionHeight) > cr.y ) {
               val x1 = (it.span.start * scale).toInt
               val x2 = (it.span.stop  * scale).toInt
               if( x1 < (cr.x + cr.width) && x2 > cr.x ) {
//                  g2.setColor( Color.black )
                  g2.setColor( colrRegion )
                  g2.fillRect( x1, y, (x2 - x1), regionHeight )
                  g2.clipRect( x1, y, (x2 - x1), regionHeight )
                  g2.setColor( Color.white )
                  g2.drawString( it.name, x1 + 4, y + fm.getAscent + 2 )
                  g2.setClip( clipOrig )
               }
            }
            y + trackHeight
         }
      }
   }

   def button( label: String )( action: => Unit ) : JButton = {
      val b = new JButton( new AbstractAction( label ) {
         def actionPerformed( e: ActionEvent ) { action }
      })
      b.setFocusable( false )
      b.putClientProperty( "JButton.buttonType", "bevel" )
      b
   }

   def frame( label: String, cleanUp: () => Unit ) : JFrame = {
      val f = new JFrame( label )
      f.setResizable( false )
      f.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE )
      f.addWindowListener( new WindowAdapter {
         override def windowClosing( e: WindowEvent ) {
            f.dispose()
            try {
               cleanUp()
            } finally {
               sys.exit( 0 )
            }
         }
      })
      f
   }

   def showFrame( f: JFrame ) {
      f.pack()
      f.setLocationRelativeTo( null )
      f.setVisible( true )
   }

   def collections[ S <: Sys[ S ]]( tup: (S, () => Unit) ) {
      val (system, cleanUp) = tup

      val infra = system.atomic { implicit tx => System[ S ]}
      import infra._

      val id = system.atomic { implicit tx => tx.newID() }

      val cnt = system.atomic { implicit tx =>
         tx.newIntVar( id, 0 )
      }

      val rnd = new scala.util.Random( 1L )

      def scramble( s: String ) : String = {
         val sb = s.toBuffer
         Seq.fill[ Char ]( s.length )( sb.remove( rnd.nextInt( sb.size ))).mkString
      }

      def newRegion()( implicit tx: S#Tx ) : EventRegion = {
         val c = cnt.get + 1
         cnt.set( c )
         val name    = "Region #" + c
         val len     = rnd.nextInt( 10 ) + 1
         val start   = rnd.nextInt( 21 - len )
         val r       = EventRegion( name, Span( start * 44100L, (start + len) * 44100L ))
//         println( "Region(" + r.name.value + ", " + r.start.value + ", " + r.stop.value + ")" )
         r
      }

      val tr   = new TrackView

      val coll = system.atomic { implicit tx =>
         val res = RegionList.empty
         res.changed.react { (tx, update) => update match {
            case RegionList.Added( _, idx, r ) =>
               implicit val _tx = tx
               val name    = r.name.value
               val span    = r.span.value
               defer {
                  tr.insert( idx, new TrackItem( name, span ))
               }
            case RegionList.Removed( _, idx, r ) =>
               defer { tr.removeAt( idx )}
         }}
         res
      }

      val f    = frame( "Reaction Test 2", cleanUp )
      val cp   = f.getContentPane
      val actionPane = Box.createHorizontalBox()
      actionPane.add( button( "Add last" ) {
         system.atomic { implicit tx =>
            coll.add( newRegion() )
         }
      })
      actionPane.add( button( "Remove first" ) {
         system.atomic { implicit tx =>
            if( coll.size > 0 ) coll.removeAt( 0 )
         }
      })
      actionPane.add( button( "Random rename" ) {
         system.atomic { implicit tx =>
            if( coll.size > 0 ) {
               val r    = coll.apply( rnd.nextInt( coll.size ))
               r.name   = scramble( r.name.value )
            }
         }
      })

      cp.add( tr, BorderLayout.CENTER )
      cp.add( actionPane, BorderLayout.SOUTH )

      showFrame( f )
   }

   def warn( message: String ) {
      new Throwable( message ).printStackTrace()
   }
}
package de.sciss.lucre
package expr

import stm.{TxnSerializer, Mutable, Sys}
import event.{Event, Compound, Invariant, Decl}
import annotation.tailrec
import collection.immutable.{IndexedSeq => IIdxSeq}

class Regions[ S <: Sys[ S ]]( val strings: Strings[ S ], val longs: Longs[ S ], val spans: Spans[ S ]) {
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
         def span_# : spans.Var

         final def name( implicit tx: Tx ) : StringEx = name_#.get
         final def name_=( value: StringEx )( implicit tx: Tx ) { name_#.set( value )}

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

      def span( implicit tx: Tx ) : SpanEx
      def span_=( value: SpanEx )( implicit tx: Tx ) : Unit
      def span_# : SpanEx
   }

   trait Region extends RegionLike with Mutable[ S ] {
      override def toString = "Region" + id
   }

   object EventRegion extends Decl[ S, EventRegion ] {
      // we need to mix Renamed and Moved which are case classes,
      // hence to ensure sweet OR operation, we gotta say that
      // Changed extends Product and Serializable
      sealed trait Changed extends Update with Product with Serializable { def r: EventRegion }
//         final case class Changed( r: EventRegion ) extends ChangedLike
      final case class Renamed( r: EventRegion, change: event.Change[ String ]) extends Changed
      final case class Moved(   r: EventRegion, change: event.Change[ Span ]) extends Changed

      declare[ Renamed ]( _.renamed )
      declare[ Moved   ]( _.moved   )
//         declare[ Changed ]( _.changed )

      def apply( name: StringEx, span: SpanEx )
               ( implicit tx: Tx ) : EventRegion = new New( name, span, tx )

      private sealed trait Impl extends RegionLike.Impl with EventRegion {
         final lazy val renamed  = name_#.changed.map( Renamed( this, _ ))
         final lazy val moved    = span_#.changed.map( Moved( this, _ ))
//            final lazy val changed  = (renamed | moved).map( ch => Changed( ch.r ))

//            // a bit stupid, because the upper bound is Changed with Product
//            final lazy val changed: Event[ S, Changed, EventRegion ]  = renamed | moved

         final lazy val changed = renamed | moved

//            final protected def sources( implicit tx: S#Tx ) = IIdxSeq( (name_#, 1 << 0), (span_#, 1 << 1) )   // OUCH XXX
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
   trait EventRegion extends RegionLike with Invariant[ S, EventRegion.Update ]
   with Compound[ S, EventRegion, EventRegion.type ] {
      import EventRegion._

      def renamed: Event[ S, Renamed, EventRegion ]
      def moved:   Event[ S, Moved,   EventRegion ]
      def changed: Event[ S, Changed, EventRegion ]
//         final def renamed = name_#.changed.map( Renamed( this, _ ))
//         final def moved   = span_#.changed.map( Moved(   this, _ ))

      override def toString = "Region" + id
   }

   object RegionList extends Decl[ S, RegionList ] {
//         sealed trait Change
      sealed trait Collection extends Update { def l: RegionList; def idx: Int; def region: EventRegion }
      final case class Added(   l: RegionList, idx: Int, region: EventRegion ) extends Collection
      final case class Removed( l: RegionList, idx: Int, region: EventRegion ) extends Collection
      final case class Element( l: RegionList, changes: IIdxSeq[ EventRegion.Changed ]) extends Update

      declare[ Collection ]( _.collectionChanged )
//         declare[ Update ]( _.changed )
      declare[ Element ]( _.elementChanged )

      def empty( implicit tx: Tx ) : RegionList = new New( tx )

      private sealed trait Impl extends RegionList {
         final lazy val collectionChanged = event[ Collection ]
         final lazy val elementChanged    = collection( (r: Elem) => r.changed ).map( Element( this, _ ))
         final lazy val changed           = collectionChanged | elementChanged

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

         final def insert( idx: Int, r: Elem )( implicit tx: S#Tx ) {
            if( idx < 0 ) throw new IllegalArgumentException( idx.toString )
            @tailrec def step( i: Int, pred: S#Var[ LO ]) {
               if( i == idx ) insert( pred, r, idx ) else pred.get match {
                  case None => throw new IndexOutOfBoundsException( idx.toString )
                  case Some( l ) => step( i + 1, l.next_# )
               }
            }
            step( 0, headRef )
         }

         private def insert( pred: S#Var[ LO ], r: Elem, idx: Int )( implicit tx: S#Tx ) {
            val l = LinkedList[ EventRegion ]( r, pred.get )
            pred.set( Some( l ))
            sizeRef.transform( _ + 1 )
//               r.name_#.addReactor( regionRenamed )
            elementChanged += r
            collectionChanged( Added( this, idx, r ))
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

         final def apply( idx: Int )( implicit tx: S#Tx ) : Elem = {
            if( idx < 0 ) throw new IllegalArgumentException( idx.toString )
            @tailrec def step( i: Int, pred: S#Var[ LO ]) : Elem = {
               pred.get match {
                  case None => throw new IndexOutOfBoundsException( idx.toString )
                  case Some( l ) =>
                     if( i == idx ) l.value
                     else step( i + 1, l.next_# )
               }
            }
            step( 0, headRef )
         }

         final def remove( r: Elem )( implicit tx: S#Tx ) : Boolean = {
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

         private def remove( pred: S#Var[ LO ], lr: L, idx: Int )( implicit tx: S#Tx ) {
            val r = lr.value
            pred.set( lr.next )
            sizeRef.transform( _ - 1 )
            elementChanged -= r
            collectionChanged( Removed( this, idx, r ))
         }

         final def indexOf( r: Elem )( implicit tx: S#Tx ) : Int = {
            @tailrec def step( i: Int, pred: S#Var[ LO ]) : Int = {
               pred.get match {
                  case None => -1
                  case Some( l ) =>
                     if( l.value == r ) i else step( i + 1, l.next_# )
               }
            }
            step( 0, headRef )
         }
      }

      private final class New( tx0: Tx ) extends Impl {
         protected val targets         = Invariant.Targets[ S ]( tx0 )
         protected val sizeRef         = tx0.newIntVar( id, 0 )
         protected val headRef         = tx0.newVar[ LO ]( id, None )
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

   trait RegionList extends Invariant[ S, RegionList.Update ]
   with Compound[ S, RegionList, RegionList.type ] {
      import RegionList._

      protected type Elem  = EventRegion
      protected type L     = LinkedList[ Elem ]
      protected type LO    = Option[ L ]

      def size( implicit tx: S#Tx ) : Int
      def insert( idx: Int, r: Elem )( implicit tx: S#Tx ) : Unit
      final def add( r: Elem )( implicit tx: S#Tx ) { insert( size, r )}
      def removeAt( idx: Int )( implicit tx: S#Tx ) : Unit
      def indexOf( r: Elem )( implicit tx: S#Tx ) : Int
      def remove( r: Elem )( implicit tx: S#Tx ) : Boolean
      def apply( idx: Int )( implicit tx: S#Tx ) : Elem

      def collectionChanged:  Ev[ Collection ]
      def elementChanged:     Ev[ Element ]
      def changed:            Ev[ Update ]

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
}

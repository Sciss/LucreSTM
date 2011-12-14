package de.sciss.lucrestm
package fluent

import annotation.tailrec
import collection.immutable.{IndexedSeq => IIdxSeq}

object ConfluentTest extends App {
   val sys = Confluent()

   sealed trait ListElemOption[ A ] { def toOption: Option[ ListElem[ A ]]}
   type E[ A ] = ListElemOption[ A ] with MutableOption[ Confluent ]
   final class ListEmptyElem[ A ] extends ListElemOption[ A ] with EmptyMutable { def toOption = None }

   type ID        = Confluent#ID
   type Tx        = Confluent#Tx
   type Ref[ ~ ]  = Confluent#Ref[ ~ ]
   type Val[ ~ ]  = Confluent#Val[ ~ ]

   trait ListElem[ A ] extends ListElemOption[ A ] with Mutable[ Confluent ] {
      protected def nextRef: Ref[ E[ A ]]
      protected def valueRef: Val[ A ]

      final def toOption = Some( this )
      final def value( implicit tx: Tx ) : A = valueRef.get
      final def value_=( a: A )( implicit tx: Tx ) { valueRef.set( a )}
      final def next( implicit tx: Tx ) : E[ A ] = nextRef.get // .toOption

      final def next_=( elem: E[ A ])( implicit tx: Tx ) {
         nextRef.set( elem )
      }

      final protected def writeData( out: DataOutput ) {
         valueRef.write( out )
         nextRef.write( out )
      }

      final protected def disposeData()( implicit tx: Confluent#Tx ) {
         valueRef.dispose()
         nextRef.dispose()
      }

      override def toString = "Elem" + id
   }

   trait Access extends Mutable[ Confluent ] {
      me =>

      protected def headRef : Ref[ E[ Int ]]
      final def head( implicit tx: Tx ) : E[ Int ] = headRef.get
      final def head_=( elem: E[ Int ])( implicit tx: Tx ) { headRef.set( elem )}

      override def toString = "Access" + id

      final protected def writeData( out: DataOutput ) {
         headRef.write( out )
      }
      final protected def disposeData()( implicit tx: Confluent#Tx ) {
         headRef.dispose()
      }

      final def print()( implicit tx: Tx ) : this.type = {
         @tailrec def step( elem: E[ Int ], seq: IIdxSeq[ Int ]) : IIdxSeq[ Int ] = elem.toOption match {
            case None => seq
            case Some( e ) => step( e.next, seq :+ e.value )
         }
         println( step( head, IIdxSeq.empty ).mkString( "in " + id.shortString + ": ", ", ", "" ))
         this
      }

      final def update( implicit tx: Tx ) : Access = new Access {
         val id      = sys.updateID( me.id )
         val headRef = me.headRef
      }
   }

   implicit val reader = new MutableOptionReader[ ID, Tx, E[ Int ]] {
      implicit def me = this

      def empty : E[ Int ] = new ListEmptyElem[ Int ]
      def readData( in: DataInput, _id: Confluent#ID )( implicit tx: Tx ) : E[ Int ] = new ListElem[ Int ] {
         import tx._
         val id         = _id
         val valueRef   = readVal[ Int ]( id, in )
         val nextRef    = readOptionRef[ E[ Int ]]( id, in )
      }
   }

   val empty : E[ Int ] = new ListEmptyElem[ Int ]

   def newElem( i: Int )( implicit tx: Tx ) : ListElem[ Int ] = new ListElem[ Int ] {
      import tx._
      val id         = newID()
      val valueRef   = newVal[ Int ]( id, i )
      val nextRef    = newOptionRef[ E[ Int ]]( id, empty )
   }

   val acc0 = sys.atomic { implicit tx => new Access {
      import tx._
      val id      = tx.newID()
      val headRef = newOptionRef[ E[ Int ]]( id, empty )
   }}

   val acc1 = sys.atomic { implicit tx =>
      val _acc1   = acc0.update
      val _w0     = newElem( 2 )
      val _w1     = newElem( 1 )
      _w0.next    = _w1
      _acc1.head = _w0
      _acc1.print()
   }

   val acc2 = sys.atomic { implicit tx =>
      val _acc2   = acc1.update
      val _w0     = acc1.head.toOption.get
      val _w1     = _w0.next.toOption.get
      _w0.next    = empty
      _w1.next    = _w0
      _acc2.head = _w1
      _acc2.print()
   }

   println( "\nDone." )
}
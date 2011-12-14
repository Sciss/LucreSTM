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

      def num: Int

      final def toOption = Some( this )
      final def value( implicit tx: Tx ) : A = valueRef.get
      final def value_=( a: A )( implicit tx: Tx ) { valueRef.set( a )}
      final def next( implicit tx: Tx ) : E[ A ] = nextRef.get // .toOption

      final def next_=( elem: E[ A ])( implicit tx: Tx ) {
         nextRef.set( elem )
      }

      final protected def writeData( out: DataOutput ) {
         out.writeInt( num )
         valueRef.write( out )
         nextRef.write( out )
      }

      final protected def disposeData()( implicit tx: Confluent#Tx ) {
         valueRef.dispose()
         nextRef.dispose()
      }

      override def toString = "w" + num + id
   }

   object Access {
      implicit val Reader : MutableReader[ ID, Tx, Access ] = new MutableReader[ ID, Tx, Access ] {
         def readData( in: DataInput, _id: ID )( implicit tx: Tx ) : Access = new Access {
            val id      = _id
            val headRef = tx.readOptionRef[ E[ Int ]]( id, in )
         }
      }
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
         @tailrec def step( elem: E[ Int ], seq: IIdxSeq[ String ]) : IIdxSeq[ String ] = {
            elem.toOption match {
               case None => seq
               case Some( e ) => step( e.next, seq :+ ("w" + e.num + "(x=" + e.value + ")") )
            }
         }
         println( step( head, IIdxSeq.empty ).mkString( "in " + id.shortString + ": ", ", ", "" ))
         this
      }

//      final def update( implicit tx: Tx ) : Access = sys.update( this )
//      new Access {
//         val id      = sys.updateID( me.id )
//         val headRef = me.headRef
//      }
   }

   implicit val reader = new MutableOptionReader[ ID, Tx, E[ Int ]] {
      implicit def me = this

      def empty : E[ Int ] = new ListEmptyElem[ Int ]
      def readData( in: DataInput, _id: Confluent#ID )( implicit tx: Tx ) : E[ Int ] = new ListElem[ Int ] {
         import tx._
         val id         = _id
         val num        = in.readInt()
         val valueRef   = readVal[ Int ]( id, in )
         val nextRef    = readOptionRef[ E[ Int ]]( id, in )
      }
   }

   val empty : E[ Int ] = new ListEmptyElem[ Int ]

   def newElem( _num: Int, i: Int )( implicit tx: Tx ) : ListElem[ Int ] = new ListElem[ Int ] {
      import tx._
      val num        = _num
      val id         = newID()
      val valueRef   = newVal[ Int ]( id, i )
      val nextRef    = newOptionRef[ E[ Int ]]( id, empty )
   }

   val (acc0, path0) = sys.atomic { implicit tx =>
      val _acc0 = new Access {
         import tx._
         val id      = tx.newID()
         val headRef = newOptionRef[ E[ Int ]]( id, empty )
      }
      val _w0     = newElem( 0, 2 )
      val _w1     = newElem( 1, 1 )
      _w0.next    = _w1
      _acc0.head = _w0
      (_acc0.print(), sys.path)
   }

   val (acc1, path1) = sys.atomic { implicit tx =>
      val _acc1   = sys.update( acc0 )
      val _w0     = _acc1.head.toOption.get
      val _w1     = _w0.next.toOption.get
      _w0.next    = empty
      _w1.next    = _w0
      _acc1.head = _w1
      (_acc1.print(), sys.path)
   }

   val (acc2, path2) = sys.fromPath( path0 ) { implicit tx =>
      val _acc2   = sys.update( acc0 )
      val _w2     = newElem( 2, 1 )
      val _w0     = _acc2.head.toOption.get
      val _w1     = _w0.next.toOption.get
      _w1.next    = _w2
      _acc2.head  = _w1
      (_acc2.print(), sys.path)
   }

   val (acc3, path3) = sys.fromPath( path1 ) { implicit tx =>
      val _acc3   = sys.update( acc1 )
      val _acc2m  = sys.update( acc2 ) // sys.meld( acc2 )
      val _w1r    = _acc2m.head.toOption.get
      _w1r.value  = _w1r.value + 2
      val _w2r    = _w1r.next.toOption.get
      _w2r.value  = _w2r.value + 2
      val _w1l    = _acc3.head.toOption.get
      val _w0l    = _w1l.next.toOption.get
      _w0l.next   = _w1r
      (_acc3.print(), sys.path)
   }

   println( "\nDone." )
}
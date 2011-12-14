package de.sciss.lucrestm
package fluent

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
   }

   trait Access extends Mutable[ Confluent ] {
      protected def headRef : Ref[ E[ Int ]]
      final def head( implicit tx: Tx ) : E[ Int ] = headRef.get
      final def head_=( elem: E[ Int ])( implicit tx: Tx ) { headRef.set( elem )}
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

   val access = sys.atomic { implicit tx => new Access {
      import tx._
      val id      = tx.newID()
      val headRef = newOptionRef[ E[ Int ]]( id, empty )
      protected def writeData( out: DataOutput ) {
         headRef.write( out )
      }
      protected def disposeData()( implicit tx: Confluent#Tx ) {
         headRef.dispose()
      }
   }}

   sys.atomic { implicit tx =>
      val _w0     = newElem( 2 )
      val _w1     = newElem( 1 )
      _w0.next    = _w1
      access.head = _w0
   }

   sys.atomic { implicit tx =>
      val _w0     = access.head.toOption.get
      val _w1     = _w0.next.toOption.get
      _w0.next    = empty
      _w1.next    = _w0
      access.head = _w1
   }

   println( "yo." )
}
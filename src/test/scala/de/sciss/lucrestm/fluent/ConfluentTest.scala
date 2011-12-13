package de.sciss.lucrestm
package fluent

import fluent.ConfluentTest.ListEmptyElem


object ConfluentTest extends App {
   val sys = Confluent()

   sealed trait ListElemOption[ A ] { def toOption: Option[ ListElem[ A ]]}
   type E[ A ] = ListElemOption[ A ] with MutableOption[ Confluent ]
   final class ListEmptyElem[ A ] extends ListElemOption[ A ] with EmptyMutable { def toOption = None }
   trait ListElem[ A ] extends ListElemOption[ A ] with Mutable[ Confluent ] {
      protected def prevRef: Confluent#Ref[ E[ A ]]
      protected def nextRef: Confluent#Ref[ E[ A ]]
      protected def valueRef: Confluent#Val[ A ]

      final def toOption = Some( this )
      final def value( implicit tx: Confluent#Tx ) : A = valueRef.get
      final def value_=( a: A )( implicit tx: Confluent#Tx ) { valueRef.set( a )}
      final def prevOption( implicit tx: Confluent#Tx ) : Option[ ListElem[ A ]] = prevRef.get.toOption
      final def nextOption( implicit tx: Confluent#Tx ) : Option[ ListElem[ A ]] = prevRef.get.toOption

      final protected def writeData( out: DataOutput ) {
         valueRef.write( out )
         prevRef.write( out )
         nextRef.write( out )
      }

      final protected def disposeData()( implicit tx: Confluent#Tx ) {
         valueRef.dispose()
         prevRef.dispose()
         nextRef.dispose()
      }
   }

   trait Access extends Mutable[ Confluent ] {
      def headRef : Confluent#Ref[ E[ Int ]]
   }

   implicit val reader = new MutableOptionReader[ Confluent, E[ Int ]] {
      implicit def me = this

      def empty : E[ Int ] = new ListEmptyElem[ Int ]
      def readData( in: DataInput, _id: Confluent#ID ) : E[ Int ] = new ListElem[ Int ] {
         val id         = _id
         val valueRef   = sys.readVal[ Int ]( in )
         val prevRef    = sys.readOptionRef[ E[ Int ]]( in )
         val nextRef    = sys.readOptionRef[ E[ Int ]]( in )
      }
   }

   val access = sys.atomic { implicit tx => new Access {
      val id      = sys.newID()
      val headRef = newOptionRef[ E[ Int ]]( new ListEmptyElem[ Int ])
      protected def writeData( out: DataOutput ) {
         headRef.write( out )
      }
      protected def disposeData()( implicit tx: Confluent#Tx ) {
         headRef.dispose()
      }
   }}
}
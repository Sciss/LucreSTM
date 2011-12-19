package de.sciss.lucrestm
package fluent

object ReferenceTest extends App {
   val sys = Confluent()

   type Tx = Confluent#Tx

   trait Expr[ A ] {
      def eval( implicit tx: Tx ) : A
   }

   trait StringRef extends Expr[ String ] {
      def append( other: StringRef ) : StringRef
   }

   trait LongRef extends Expr[ Long ] {
      def +( other: LongRef ) : LongRef
      def max( other: LongRef ) : LongRef
      def min( other: LongRef ) : LongRef
   }

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
}
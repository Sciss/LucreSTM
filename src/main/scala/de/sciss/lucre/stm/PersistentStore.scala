package de.sciss.lucre
package stm

object PersistentStore {
   trait KeyWriter[ K ] {
      def writeKey( key: K, out: DataOutput ) : Unit
   }
   trait ValueWriter[ V, Ser[ _ ]] {
      def writeValue( value: V, ser: Ser[ V ], out: DataOutput ) : Unit
   }
   trait ValueReader[ V, Ser[ _ ]] {
      def readValue( in: DataInput, ser: Ser[ V ]) : V
   }
   type Put[ K, V, Ser[ _ ]] = KeyWriter[ K ] with ValueWriter[ V, Ser ]
   type Get[ K, V, Ser[ _ ]] = KeyWriter[ K ] with ValueReader[ V, Ser ]

}
trait PersistentStore[ Txn ] {
   import PersistentStore._

//   def put(      key: Array[ Byte ])( writer: DataOutput => Unit )( implicit tx: Txn ) : Unit
//   def get[ A ]( key: Array[ Byte ])( reader: DataInput  => A    )( implicit tx: Txn ) : Option[ A ]
//   def contains( key: Array[ Byte ])( implicit tx: Txn ) : Boolean
//   def remove(   key: Array[ Byte ])( implicit tx: Txn ) : Unit

   def put[ K, V, Ser[ _ ]]( key: K, value: V )( implicit tx: Txn, ser: Ser[ V ], writer: Put[ K, V, Ser ]) : Unit
   def get[ K, V, Ser[ _ ]]( key: K )( implicit tx: Txn, ser: Ser[ V ], writer: Get[ K, V, Ser ]) : Option[ V ]
   def contains[ K ]( key: K )( implicit tx: Txn, writer: KeyWriter[ K ]) : Boolean
   def remove[ K ]( key: K )( implicit tx: Txn, writer: KeyWriter[ K ]) : Boolean
   def numEntries( implicit tx: Txn ) : Int
   def close() : Unit
}

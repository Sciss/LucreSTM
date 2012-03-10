package de.sciss.lucre
package stm

object PersistentStore {
//   trait KeyWriter[ K ] {
//      def writeKey( key: K, out: DataOutput ) : Unit
//   }
//   trait ValueWriter[ Ser[ _ ]] {
//      def writeValue[ V ]( value: V, ser: Ser[ V ], out: DataOutput ) : Unit
//   }
//   trait ValueReader[ Txn, Ser[ _ ]] {
//      def readValue[ V ]( in: DataInput, ser: Ser[ V ])( implicit tx: Txn ) : V
//   }
//   trait Put[      K, Ser[ _ ]]    extends KeyWriter[ K ] with ValueWriter[ Ser ]
//   trait Get[ Txn, K, Ser[ _ ]]    extends KeyWriter[ K ] with ValueReader[ Txn, Ser ]
////   trait Client[ Txn, K, Ser[ _ ]] extends Put[ K, Ser ] with Get[ Txn, K, Ser ]
//
//   trait TxnClient[ S <: Sys[ S ], K ]
//   extends Put[ K, TxnWriter ]
//   with    Get[ S#Tx, K, ({ type λ[ ~ ] = TxnReader[ S#Tx, S#Acc, ~ ]})#λ ]

// how the **** can we do this?

//   /**
//    * Useful partial type application for `TxnSerializer`
//    */
//   type Serializer[ Txn, Acc, ~ ] = ({ type λ[ ~ ] = TxnSerializer[ Txn, Acc, ~ ]})#λ
}
trait PersistentStore[ Txn ] {
//   import PersistentStore._

//   def put(      key: Array[ Byte ])( writer: DataOutput => Unit )( implicit tx: Txn ) : Unit
//   def get[ A ]( key: Array[ Byte ])( reader: DataInput  => A    )( implicit tx: Txn ) : Option[ A ]
//   def contains( key: Array[ Byte ])( implicit tx: Txn ) : Boolean
//   def remove(   key: Array[ Byte ])( implicit tx: Txn ) : Unit

//   def put[ K, V, Ser[ _ ]]( key: K, value: V )( implicit tx: Txn, ser: Ser[ V ], writer: Put[ K, Ser ]) : Unit
//   def get[ K, V, Ser[ _ ]]( key: K )( implicit tx: Txn, ser: Ser[ V ], writer: Get[ Txn, K, Ser ]) : Option[ V ]
//   def contains[ K ]( key: K )( implicit tx: Txn, writer: KeyWriter[ K ]) : Boolean
//   def remove[ K ]( key: K )( implicit tx: Txn, writer: KeyWriter[ K ]) : Boolean
   def numEntries( implicit tx: Txn ) : Int
   def close() : Unit

   def put( keyFun: DataOutput => Unit )( valueFun: DataOutput => Unit )( implicit tx: Txn ) : Unit
   def get[ A ]( keyFun: DataOutput => Unit )( valueFun: DataInput => A )( implicit tx: Txn ) : Option[ A ]
   def contains( keyFun: DataOutput => Unit )( implicit tx: Txn ) : Boolean
   def remove( keyFun: DataOutput => Unit )( implicit tx: Txn ) : Boolean
}

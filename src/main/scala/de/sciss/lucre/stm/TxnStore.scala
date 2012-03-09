package de.sciss.lucre
package stm

trait TxnStore[ Txn ] {
   def put(      key: Array[ Byte ])( writer: DataOutput => Unit )( implicit tx: Txn ) : Unit
   def get[ A ]( key: Array[ Byte ])( reader: DataInput  => A    )( implicit tx: Txn ) : Option[ A ]
   def contains( key: Array[ Byte ])( implicit tx: Txn ) : Boolean
   def remove(   key: Array[ Byte ])( implicit tx: Txn ) : Unit
}

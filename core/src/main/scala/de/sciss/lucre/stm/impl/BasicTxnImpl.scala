package de.sciss.lucre.stm
package impl

import concurrent.stm.Txn

trait BasicTxnImpl[ S <: Sys[ S ]] extends Txn[ S ] {
   _: S#Tx =>

   def beforeCommit( fun: S#Tx => Unit ) {
      Txn.beforeCommit( _ => fun( this ))( peer )
   }

   def afterCommit( code: => Unit ) {
      Txn.afterCommit( _ => code )( peer )
   }
}
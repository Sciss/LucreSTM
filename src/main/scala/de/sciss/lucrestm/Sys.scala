package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => STMRef}
import concurrent.stm.InTxn
import java.io.{ObjectOutputStream, ObjectInputStream}

trait Sys[ Self <: Sys[ Self ]] {
   type Ref[ A ] <: STMRef[ Self#Tx, A ]
   type Tx <: InTxn

   def newRef[ A ]( init: A )( implicit tx: Self#Tx, ser: Serializer[ A ]) : Self#Ref[ A ]
   def atomic[ Z ]( block: Self#Tx => Z ) : Z
   def newRefArray[ A ]( size: Int ) : Array[ Self#Ref[ A ]]
//   def serRef[ A : Serializer ] : Serializer[ Self#Ref[ A ]]
   def readRef[ A ]( is: ObjectInputStream )( implicit ser: Serializer[ A ] ) : Self#Ref[ A ]
   def writeRef[ A ]( ref: Self#Ref[ A ], os: ObjectOutputStream ) : Unit
   def disposeRef[ A ]( ref: Self#Ref[ A ])( implicit tx: Self#Tx ) : Unit
}
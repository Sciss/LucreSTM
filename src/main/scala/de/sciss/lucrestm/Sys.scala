package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref}
import concurrent.stm.InTxn

trait Sys[ Self <: Sys[ Self ]] {
   type Ref[ A ] <: _Ref[ Self#Tx, A ]
   type Mut[ A ] <: Mutable[ Self#Tx, A ]
   type Tx <: InTxn

   def newRef[ A ]( init: A )( implicit tx: Self#Tx, ser: Serializer[ A ]) : Self#Ref[ A ]
   def newMut[ A <: Disposable[ Self#Tx ]]( init: A )( implicit tx: Self#Tx, ser: Serializer[ A ]) : Self#Mut[ A ]

   def atomic[ Z ]( block: Self#Tx => Z ) : Z
   def newRefArray[ A ]( size: Int ) : Array[ Self#Ref[ A ]]
//   def serRef[ A : Serializer ] : Serializer[ Self#Ref[ A ]]
   def readRef[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : Self#Ref[ A ]
   def readMut[ A <: Disposable[ Self#Tx ]]( in: DataInput )( implicit ser: Serializer[ A ]) : Self#Mut[ A ]

//   def writeRef[ A ]( ref: Self#Ref[ A ], out: DataOutput ) : Unit
//   def disposeRef[ A ]( ref: Self#Ref[ A ])( implicit tx: Self#Tx ) : Unit
}
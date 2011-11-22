package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref}
import concurrent.stm.InTxn

trait Sys[ S <: Sys[ S ]] {
   type Ref[ A ] <: _Ref[ S#Tx, A ]
   type Mut[ A ] <: Mutable[ S#Tx, A ]
   type Tx <: InTxn

   def newVal[ A ]( init: A )( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Ref[ A ]
   def newRef[ A <: Disposable[ S#Tx ]]( init: S#Mut[ A ])( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Ref[ S#Mut[ A ]]
   def newMut[ A <: Disposable[ S#Tx ]]( init: A )( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Mut[ A ]

   def atomic[ Z ]( block: S#Tx => Z ) : Z
   def newRefArray[ A ]( size: Int ) : Array[ S#Ref[ A ]]
//   def serRef[ A : Serializer ] : Serializer[ S#Ref[ A ]]
   def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : S#Ref[ A ]
   def readRef[ A <: Disposable[ S#Tx ]]( in: DataInput )( implicit ser: Serializer[ A ]) : S#Ref[ S#Mut[ A ]]
   def readMut[ A <: Disposable[ S#Tx ]]( in: DataInput )( implicit ser: Serializer[ A ]) : S#Mut[ A ]

//   def writeRef[ A ]( ref: S#Ref[ A ], out: DataOutput ) : Unit
//   def disposeRef[ A ]( ref: S#Ref[ A ])( implicit tx: S#Tx ) : Unit
}
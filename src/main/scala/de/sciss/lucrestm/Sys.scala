package de.sciss.lucrestm

import de.sciss.lucrestm.{Ref => _Ref, Val => _Val}
import concurrent.stm.InTxn

trait Sys[ S <: Sys[ S ]] {
   type Val[ A ] <: _Val[ S#Tx, A ]
//   type Mut[ +A ] <: Mutable[ S#Tx, A ]
   type Ref[ A <: Mutable[ S, A ]] <: _Ref[ S#Tx, /* S#Mut,*/ A ]
   type Tx <: InTxn
   type ID <: Disposable[ S#Tx ]

   def newVal[ A ]( init: A )( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Val[ A ]
//   def newRef[ A <: Disposable[ S#Tx ]]()( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Ref[ A ]
//   def newRef[ A <: Disposable[ S#Tx ]]( init: S#Mut[ A ])( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Ref[ A ]
   def newRef[ A <: Mutable[ S, A ]]( init: A )( implicit tx: S#Tx, reader: Reader[ A ]) : S#Ref[ A ]
//   def newMut[ A <: Disposable[ S#Tx ]]( init: A )( implicit tx: S#Tx, ser: Serializer[ A ]) : S#Mut[ A ]
   def newID( implicit tx: S#Tx ) : ID

   def atomic[ Z ]( block: S#Tx => Z ) : Z
   def newValArray[ A ]( size: Int ) : Array[ S#Val[ A ]]
   def newRefArray[ A <: Mutable[ S, A ]]( size: Int ) : Array[ S#Ref[ A ]]
//   def serRef[ A : Serializer ] : Serializer[ S#Ref[ A ]]
   def readVal[ A ]( in: DataInput )( implicit ser: Serializer[ A ]) : S#Val[ A ]
   def readRef[ A <: Mutable[ S, A ]]( in: DataInput )( implicit reader: Reader[ A ]) : S#Ref[ A ]
//   def readMut[ A <: Disposable[ S#Tx ]]( in: DataInput )( implicit ser: Serializer[ A ]) : S#Mut[ A ]

//   def readMut[ A <: Mutable[ S, A ]]( in: DataInput )( implicit ser: Serializer[ A ]) : A // S#Mut[ A ]

   def readMut[ A <: Mutable[ S, A ]]( in: DataInput )( constr: S#ID => A ) : A // S#Mut[ A ]

//   def writeRef[ A ]( ref: S#Ref[ A ], out: DataOutput ) : Unit
//   def disposeRef[ A ]( ref: S#Ref[ A ])( implicit tx: S#Tx ) : Unit
}
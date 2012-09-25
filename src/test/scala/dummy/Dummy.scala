package dummy

//trait Txn[ S <: Sys[ S ]] {
//   def newID() : S#ID
//   def newVar[ A ]( id: S#ID, init: A ) : S#Var[ A ]
//   def system: S
//
//   type I <: Sys[ I ] // { type Tx = PeerTx.type }
//
//   def PeerTx : Txn[ I ]
//}
//trait Sys[ S <: Sys[ S ]] {
//   type Tx <: Txn[ S ]
//   type Peer <: Sys[ Peer ] // parallel system
//   type Var[ A ] <: VarLike[ S#Tx, A ]
//   type ID
//}
//class Test[ S <: Sys[ S ]]( tx0: S#Tx ) {
//   def ??? : Nothing = sys.error( "TODO" )
//
//   val peerVar: S#Peer#Var[ Int ] = {
//      val sys = tx0.system
//      ???
//   }
//
//   val p2 : S#Tx#I#Var[ Int ] = {
//      val p  = tx0.PeerTx
//      val id = p.newID()
//      val vr = p.newVar( id, 0 )
////      vr.update( 1 )( p )
//      ???
//   }
//}

trait VarLike[ -Tx, A ] { def update( value: A )( implicit tx: Tx ) : Unit }

trait S2[ S <: S2[ S ]] {
//   _: S =>

   type Tx <: T2[ S /*, Tx */]

   type Var[ A ] <: VarLike[ S#Tx, A ]
   type ID

   type Peer <: S2[ Peer ]

//   def confirm( tx: S#Tx ) : S#Tx = tx
//   def convert( tx: S#Tx ) : S#Peer#Tx

   def proxyUpdate[ A ]( v: S#Peer#Var[ A ], value: A )( implicit tx: S#Tx ) : Unit
   def proxyNewID()( implicit tx: S#Tx ) : S#Peer#ID
   def proxyNewVar[ A ]( id: S#Peer#ID, init: A )( implicit tx: S#Tx ) : S#Peer#Var[ A ]

   def proxy( implicit tx: S#Tx ) : S#Peer#Tx
}

trait T2[ S <: S2[ S ] /*, T <: T2[ S, T ]*/] {
//   _: T =>

   type S1 = S

   def newID() : S#ID
   def newVar[ A ]( id: S#ID, init: A ) : S#Var[ A ]

   def system: S
   def peer: S#Peer#Tx
}

class InMemTx( val system: InMem ) extends T2[ InMem /*, InMemTx */ ] {
   def newID() {}
   def newVar[ A ]( id: InMem#ID, init: A ) : InMem#Var[ A ] = new VarLike[ InMemTx, A ] {
      def update( v: A )( implicit tx: InMemTx ) {}
   }
   def peer = this
}
class InMem extends S2[ InMem ] {
   type Tx = InMemTx
   type Var[ A ] = VarLike[ Tx, A ]
   type ID = Unit

   type Peer = InMem

//   def convert( tx: InMem#Tx ) = tx

   def proxyUpdate[ A ]( v: Var[ A ], value: A )( implicit tx: Tx ) {
      v.update( value )( tx )
   }

   def proxyNewID()( implicit tx: Tx ) {}
   def proxyNewVar[ A ]( id: ID, init: A )( implicit tx: Tx ) : Var[ A ] = tx.newVar( id, init )

   def proxy( implicit tx: Tx ) : Tx = tx
}

class DurableTx( val system: Durable, val peer: InMem#Tx ) extends T2[ Durable /*, DurableTx */ ] {
   def newID() = 33
   def newVar[ A ]( id: Durable#ID, init: A ) : Durable#Var[ A ] = new VarLike[ DurableTx, A ] {
      def update( v: A )( implicit tx: DurableTx ) {}
   }
}
class Durable extends S2[ Durable ] {
   type Tx = DurableTx
   type Var[ A ] = VarLike[ Tx, A ]
   type ID = Int

   type Peer = InMem

//   def convert( tx: InMem#Tx ) = tx

   def proxyUpdate[ A ]( v: InMem#Var[ A ], value: A )( implicit tx: Tx ) {
      v.update( value )( tx.peer )
   }

   def proxyNewID()( implicit tx: Tx ) {}
   def proxyNewVar[ A ]( id: InMem#ID, init: A )( implicit tx: Tx ) : InMem#Var[ A ] = tx.peer.newVar( id, init )

   def proxy( implicit tx: Tx ) : InMem#Tx = tx.peer
}

trait WithT2[ S <: S2[ S ]] {
   def v1 : S#Var[ Int ]
   def v2 : S#Peer#Var[ Int ]

   def test( implicit tx: S#Tx ) {
//      tx.system.confirm( tx )
      v1.update( 1 )( tx )

//      val p = tx.system.convert( tx )
//      v2.update( 1 )( p )

      tx.system.proxyUpdate( v2, 1 )
   }
}

class WithT2C[ S <: S2[ S ]]( implicit tx: S#Tx ) extends WithT2[ S ] {
   val v1 = tx.newVar( tx.newID(), 0 )
   val v2 = tx.system.proxyNewVar( tx.system.proxyNewID(), 0 )

   test
}


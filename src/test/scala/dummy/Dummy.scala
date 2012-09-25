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

trait Sys[ S <: Sys[ S ]] {
   type Tx       <: Txn[ S ]
   type Var[ A ] <: VarLike[ S#Tx, A ]
   type ID
   type Peer     <: Sys[ Peer ]

   // this indirection works, but is horrible
   def proxyUpdate[ A ]( v: S#Peer#Var[ A ], value: A )( implicit tx: S#Tx ) : Unit
   def proxyNewID()( implicit tx: S#Tx ) : S#Peer#ID
   def proxyNewVar[ A ]( id: S#Peer#ID, init: A )( implicit tx: S#Tx ) : S#Peer#Var[ A ]

   def proxy( implicit tx: S#Tx ) : S#Peer#Tx  // couldn't make use of this
}

trait Txn[ S <: Sys[ S ]] {
   def newID() : S#ID
   def newVar[ A ]( id: S#ID, init: A ) : S#Var[ A ]
   def system: S
   def peer: S#Peer#Tx // only useful for the system instance with types fixed
}

// let's make sure we can implement actual systems
class InMemTx( val system: InMem ) extends Txn[ InMem ] {
   def newID() {}
   def newVar[ A ]( id: InMem#ID, init: A ) : InMem#Var[ A ] = new VarLike[ InMemTx, A ] {
      def update( v: A )( implicit tx: InMemTx ) {}
   }
   def peer = this
}
class InMem extends Sys[ InMem ] {
   type Tx       = InMemTx
   type Var[ A ] = VarLike[ Tx, A ]
   type ID       = Unit
   type Peer     = InMem  // reflect back to ourself

   def proxyUpdate[ A ]( v: Var[ A ], value: A )( implicit tx: Tx ) {
      v.update( value )( tx )
   }
   def proxyNewID()( implicit tx: Tx ) {}
   def proxyNewVar[ A ]( id: ID, init: A )( implicit tx: Tx ) : Var[ A ] = tx.newVar( id, init )

   def proxy( implicit tx: Tx ) : Tx = tx
}

class DurableTx( val system: Durable, val peer: InMem#Tx ) extends Txn[ Durable ] {
   def newID() = 33
   def newVar[ A ]( id: Durable#ID, init: A ) : Durable#Var[ A ] = new VarLike[ DurableTx, A ] {
      def update( v: A )( implicit tx: DurableTx ) {}
   }
}
class Durable extends Sys[ Durable ] {
   type Tx = DurableTx
   type Var[ A ] = VarLike[ Tx, A ]
   type ID = Int
   type Peer = InMem

   def proxyUpdate[ A ]( v: InMem#Var[ A ], value: A )( implicit tx: Tx ) {
      v.update( value )( tx.peer )
   }
   def proxyNewID()( implicit tx: Tx ) {}
   def proxyNewVar[ A ]( id: InMem#ID, init: A )( implicit tx: Tx ) : InMem#Var[ A ] = tx.peer.newVar( id, init )

   def proxy( implicit tx: Tx ) : InMem#Tx = tx.peer
}

// let's make sure we can use the system as intended
trait TestTrait[ S <: Sys[ S ]] {
   def v1 : S#Peer#Var[ Int ]
   def v2 : S#Peer#Var[ Int ]

   def test1( implicit tx: S#Tx ) {
      tx.system.proxyUpdate( v1, 1 )
   }

   def test2( implicit tx: S#Tx ) {
// no way to get around the direct proxy calls...
//      val p = tx.peer
//      val p = tx.system.proxy( tx )
//      v2.update( 1 )( p )
   }
}

// see if we can actually create variables
class TestImpl[ S <: Sys[ S ]]( implicit tx: S#Tx ) extends TestTrait[ S ] {
   val v1 = tx.system.proxyNewVar( tx.system.proxyNewID(), 0 )
   val v2 = {
// no way to get around the direct proxy calls...
//      val p = tx.peer
//      val p = tx.system.proxy( tx )
//      p.newVar( p.newID(), 0 )
      sys.error( "???" )
   }
}

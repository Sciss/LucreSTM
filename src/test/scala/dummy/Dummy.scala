package dummy

    trait VarLike[ -Tx, A ] { def update( value: A )( implicit tx: Tx ) : Unit }

    trait Sys[ S <: Sys[ S ]] {
       type Tx       <: Txn[ S ]
       type Var[ A ] <: VarLike[ S#Tx, A ]
       type ID
       type Peer     <: Sys[ Peer ]

       // 'pop' the representation type ?!
       def fix[ A ]( v: S#Peer#Var[ A ]) : Peer#Var[ A ]
       def peer( tx: S#Tx ) : Peer#Tx

       def use[ A, B ]( v: S#Peer#Var[ A ])( fun: Peer#Tx => Peer#Var[ A ] => B )( implicit tx: S#Tx ) : B =
         fun( peer( tx ))( fix( v ))
    }

    trait Txn[ S <: Sys[ S ]] {
       def newID() : S#ID
       def newVar[ A ]( id: S#ID, init: A ) : S#Var[ A ]
       def system: S

//       def use[ A, B ]( v: S#Peer#Var[ A ])( fun: S#Peer#Tx => S#Peer#Var[ A ] => B ) : B
    }

    // let's make sure we can implement actual systems
    class InMemTx( val system: InMem ) extends Txn[ InMem ] {
       def newID() {}
       def newVar[ A ]( id: InMem#ID, init: A ) : InMem#Var[ A ] =
          new VarLike[ InMemTx, A ] {
             def update( v: A )( implicit tx: InMemTx ) {}
          }
//       def use[ A, B ]( v: InMem#Var[ A ])( fun: InMem#Tx => InMem#Var[ A ] => B ) : B =
//         fun( this )( v )
    }
    class InMem extends Sys[ InMem ] {
       type Tx       = InMemTx
       type Var[ A ] = VarLike[ Tx, A ]
       type ID       = Unit
       type Peer     = InMem  // reflect back to ourself

       def fix[ A ]( v: Var[ A ]) : Var[ A ] = v
       def peer( tx: Tx ) : Tx = tx
    }

    class DurableTx( val system: Durable, val peer: InMem#Tx ) extends Txn[ Durable ] {
       def newID() = 33
       def newVar[ A ]( id: Durable#ID, init: A ) : Durable#Var[ A ] =
          new VarLike[ DurableTx, A ] {
            def update( v: A )( implicit tx: DurableTx ) {}
          }
//       def use[ A, B ]( v: InMem#Var[ A ])( fun: InMem#Tx => InMem#Var[ A ] => B ) : B =
//         fun( peer )( v )
    }
    class Durable extends Sys[ Durable ] {
       type Tx       = DurableTx
       type Var[ A ] = VarLike[ Tx, A ]
       type ID       = Int
       type Peer     = InMem

       def fix[ A ]( v: InMem#Var[ A ]) : InMem#Var[ A ] = v
       def peer( tx: Tx ) : InMem#Tx = tx.peer
    }

    // let's make sure we can use the system as intended
    trait TestTrait[ S <: Sys[ S ]] {
       def v : S#Peer#Var[ Int ]

       def test( implicit tx: S#Tx ) {
          val s          = tx.system
          implicit val p = s.peer( tx )
          val vf         = s.fix( v ) // not cool...
          vf()           = 1

          tx.system.use( v ) { implicit tx => _.update( 2 )}

//          tx.use( v ) { implicit tx => _.update( 3 )}
       }
    }

    // see if we can actually create variables
    class TestImpl[ S <: Sys[ S ]]( implicit tx: S#Tx ) extends TestTrait[ S ] {
       val v = {
          val s          = tx.system
          implicit val p = s.peer( tx )
          val id         = p.newID()
          p.newVar( id, 0 )
       }
    }

package de.sciss.lucrestm

import concurrent.stm.{InTxn, Ref}
import concurrent.stm.Ref.View

final class LucreRef[ /* @specialized */ A ]( lucre: LucreSTM ) extends Ref[ A ] {
   private def notYetImplemented : Nothing = sys.error( "Not yet implemented" )

//   private lazy val id: Array[ Byte ] = lucre.newID()

   def swap( v: A )( implicit txn: InTxn ) : A = notYetImplemented // peer.swap( v )( txn )

   def transform( f: (A) => A )( implicit txn: InTxn ) : Unit = notYetImplemented // { peer.transform( f )( txn )}

   def transformIfDefined( pf: PartialFunction[ A, A ])( implicit txn: InTxn ) : Boolean =
      notYetImplemented // peer.transformIfDefined( pf )( txn )

   def set( v: A )( implicit txn: InTxn ) : Unit = notYetImplemented // { peer.set( v )( txn )}

   def trySet( v: A )( implicit txn: InTxn ) : Boolean = notYetImplemented // peer.trySet( v )( txn )

   def get( implicit txn: InTxn ) : A = notYetImplemented // peer.get( txn )

   def getWith[ Z ]( f: (A) => Z )( implicit txn: InTxn ) : Z = notYetImplemented // peer.getWith[ Z ]( f )( txn )

   def relaxedGet( equiv: (A, A) => Boolean )( implicit txn: InTxn ) : A = notYetImplemented // peer.relaxedGet( equiv )( txn )

   def single : View[ A ] = notYetImplemented

}
package de.sciss.lucrestm

import concurrent.stm.{InTxn, Ref}
import concurrent.stm.Ref.View

/**
 * A plain database backed up reference. This does not offer any sort of caching.
 */
final class LucreRef[ /* @specialized */ A ]( lucre: LucreSTM ) extends Ref[ A ] {
   private def notYetImplemented : Nothing = sys.error( "Not yet implemented" )

   lazy val id: Int = lucre.newID()

   def swap( v: A )( implicit txn: InTxn ) : A = {
      val res = get
      set( v )
      res
   }

   def transform( f: (A) => A )( implicit txn: InTxn ) { set( f( get ))}

   def transformIfDefined( pf: PartialFunction[ A, A ])( implicit txn: InTxn ) : Boolean = {
      val v = get
      if( pf.isDefinedAt( v )) {
         set( pf( v ))
         true
      } else {
         false
      }
   }

   def set( v: A )( implicit txn: InTxn ) {
      lucre.write( id )( _.writeObject( v ))
   }

   def trySet( v: A )( implicit txn: InTxn ) : Boolean = {
      notYetImplemented
   }

   def get( implicit txn: InTxn ) : A = {
      lucre.read[ A ]( id )( _.readObject.asInstanceOf[ A ])
   }

   def getWith[ Z ]( f: (A) => Z )( implicit txn: InTxn ) : Z = f( get )

   def relaxedGet( equiv: (A, A) => Boolean )( implicit txn: InTxn ) : A = {
      notYetImplemented
   }

   def single : View[ A ] = notYetImplemented
}
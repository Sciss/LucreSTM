package de.sciss.lucrestm

import java.io.{ObjectInputStream, ObjectOutputStream}

trait Serializer[ A ] {
   def write( v: A, os: ObjectOutputStream ) : Unit
   def read( is: ObjectInputStream ) : A
}
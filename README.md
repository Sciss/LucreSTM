# LucreSTM

__Note__ this is 0.21-SNAPSHOT. See tag v0.20 for the latest release.

## statement

LucreSTM is provides a software transactional memory with persistent backend, as well as reactive observer system. The STM is basically a wrapper around Scala-STM, but with an API which supports custom persistence and which can be extended to support confluent and quasi-retroactive persistent as implemented by the [TemporalObjects](https://github.com/Sciss/TemporalObjects) project. The reactive system implements event graphs which also can be persistent, along with live observers.

LucreSTM is (C)opyright 2011&ndash;2012 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](https://raw.github.com/Sciss/LucreSTM/master/licenses/LucreSTM-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`

Further reading:

 - Bronson, N.G. and Chafi, H. and Olukotun, K., "CCSTM: A library-based STM for Scala," in Proceedings of the First Scala Workshop, 2010.
 - Gasiunas, V. and Satabin, L. and Mezini, M. and Núñez, A. and Noyé, J., "EScala: Modular Event-Driven Object Interactions in Scala," in Proceedings of the tenth international conference on Aspect-oriented software development, pp. 227--240, 2011.

## requirements / installation

LucreSTM builds with sbt 0.11 against Scala 2.9.1. It depends on [Scala-STM](http://nbronson.github.com/scala-stm/) 0.5 and currently includes a Berkeley DB JE 5 based database backend.

## linking to LucreSTM

The following dependency is necessary:

    "de.sciss" %% "lucrestm" % "0.20"

## documentation

At the moment, there are only sparse scaladocs, I'm afraid (run `sbt doc`). The basic concept:

### STM

An STM implementations uses the trait `Sys` which defines the actual transaction `Tx` and reference `Var` types, as well as identifiers `ID` which are used for persistence, and an access type `Acc` which is used by the confluent implementation. Actual implementations are in package `de.sciss.lucre.stm.impl`, they include a ephemeral but persisted database STM `BerkeleyDB`, an ephemeral in-memory implementation `InMemory`, and a confluent implementation `Confluent` which is not optimised or persisted to disk, but merely included as a proof of concept. (A fully fledged confluent STM is in project TemporalObjects).

The `Sys` provides an `atomic` method to spawn a new transaction. Unlike Scala-STM, references can only be created within a transaction, thus their constructors are methods in `S#Tx` (which is a sub type of trait `Txn`). The underlying Scala-STM transaction can be read via `tx.peer`. LucreSTM currently does not implement any protocol system such as SBinary. It thus asks for readers and writers (both which combine into serializers) when constructing or reading in a reference.

The __life cycle__ of a reference, in following just called Var, is as follows: It comes into existance through `tx.newVar` (or one of the specialised methods such as `tx.newIntVar`). Apart from the initial value, this call requires to pass in an `S#ID` __parent_ identifier and a `TxnSerializer` for the type of Var. The parent ID is the ID of the mutable objects in which the Var will reside. You can think of the ID as an opaque object, behind the scenes this is used by the confluent system to retrieve the access path of the parent. When you instantiate a new mutable object with transactional semantics, you can get a new ID by calling `tx.newID()`.

The serializer has a non-transactional `write` method, and a transactional `read` method:

```scala
    trait TxnSerializer[ -Txn, -Access, A ] {
       def write( v: A, out: DataOutput ) : Unit
       def read( in: DataInput, access: Access )( implicit tx: Txn ) : A
    }
```

Again, the `Access` type (corresponding to `S#Acc`) is used by the confluent system, and you can treat it as an opaque entity given to you. As you can see, the `write` method does not have access to any transactional context, while the deserialization requires the implicit `tx` argument as you might be dealing with a composite object whose members will be restored by successive calls to `tx.readVar`. Standard serializers are provided for primitive types and common Scala types such as `Option`, `Either`, `Tuple2`, ..., and various collections from `scala.collection.immutable`.

Continuing in the life cycle, once you have create a Var, you can read and write values to it using the `get` and `set` methods which, like a normal Scala-STM Ref, require an implicit transaction context.

Finally, objects must be disposed. We require explicit garbage disposal instead of superimposing something like weak reference sets. Therefore, it is necessary to call `dispose` on refs once they are not used any more. This requires some thinking about their visibility, but should improve performance compared to automatic garbage collection (also, GC would imply that the system initiates transactions itself, something considered highly problematic).

#### STM Example

This is taken from the test sources. For conciseness, disposal is not demonstrated. Note how we use `Mutable` and `MutableSerializer` to minimise implementation costs. `Mutable` makes sure we have an `id` field, it also provides a `write` method which writes out that id and then calls `writeData`, similarly with `dispose` and `disposeData`. More importantly, it provides `hashCode` and `equals` based on the identifier. `MutableSerializer` has a convenient `write` method implementation, and reads the identifier, passing it into the only method left to implement, `readData`.

```scala
    import de.sciss.lucre._
    import stm.{Durable => S, _}

    object Person {
       implicit object ser extends TxnMutableSerializer[ S, Person ] {
          def readData( in: DataInput, _id: S#ID )( implicit tx: S#Tx ) : Person = new Person {
             val id      = _id
             val name    = in.readString()
             val friends = tx.readVar[ List[ Person ]]( id, in )
          }
       }
    }
    trait Person extends Mutable[ S ] {
       def name: String
       def friends: S#Var[ List[ Person ]]
       protected def disposeData()( implicit tx: S#Tx ) { friends.dispose() }
       protected def writeData( out: DataOutput ) {
          out.writeString( name )
          friends.write( out )
       }
    }

    val pre  = IndexedSeq( "Adal", "Bern", "Chlod", "Diet", "Eg",   "Fried" )
    val post = IndexedSeq( "bert", "hard", "wig",   "mar",  "mund", "helm"  )
    val rnd  = new scala.util.Random()

    // create a person with random name and no friends
    def newPerson()( implicit tx: S#Tx ) = new Person {
       val id      = tx.newID()
       val name    = pre( rnd.nextInt( pre.size )) + post( rnd.nextInt( post.size ))
       val friends = tx.newVar[ List[ Person ]]( id, Nil )
    }

    val dir  = new java.io.File( sys.props( "user.home" ), "person_db" )
    dir.mkdirs()
    val s    = S( impl.BerkeleyDB.open( dir ))
    val root = s.atomic { implicit tx =>
       // read the root data set, or create a new one if the database does not exist
       s.root[ Person ]( newPerson() )
    }

    def gather( p: Person, set: Set[ Person ])( implicit tx: S#Tx ) : Set[ Person ] = {
       if( !set.contains( p )) {
          val set1 = set + p
          p.friends.get.foldLeft( set1 )( (s2, p1) => gather( p1, s2 ))
       } else set
    }

    // see who is in the database so far
    val found = s.atomic { implicit tx => gather( root, Set.empty )}
    val infos = s.atomic { implicit tx => found.map { p =>
       "Remember " + p.name + "? He's " + (p.friends.get match {
          case Nil => "lonely"
          case fs  => fs.map( _.name ).mkString( "friend of ", " and ", "" )
       })
    }}
    infos.foreach( println )

    // create a new person and make it friend of half of the population
    s.atomic { implicit tx =>
       val p = newPerson()
       val friends0 = found.filter( _ => rnd.nextBoolean() )
       val friends = if( friends0.isEmpty ) Seq( root ) else friends0
       friends.foreach { f =>
          p.friends.transform( f :: _ )
          f.friends.transform( p :: _ )
       }
       println( "Say hi to " + p.name + ". He's friend of " + friends.map( _.name ).mkString( " and " ))
    }
```

Now re-run the program to verify the persons have been persisted.

### Events

TODO!

 - The only knowledge that a system (`Sys`) has of the reaction framework is to keep track of 'live' objects.
 - The reaction framework is otherwise an independent part on top of the STM. It distinguishes between persisted and live references (even with a pure in-memory system), where a change in an observed object is propagated through a mechanism called 'tunnelling', pushing along stub nodes until a live node is found, which in turn evaluates the path in pull fashion.

## limitations, future ideas

 - This project is in early test stage, so it is not yet optimized for best possible performance, but rather for simplicity!
 - Currently all persisted types need explicit serializers, which can be tiresome. The idea was that this way we can have very fast and efficient serialization, but probably it would be helpful if we could use standard Java serialization (`@serializable`) as a default layer in the absence of explicit serializers.
 - Currently the fields of references are directly written out in the database version. This can yield to several representations of the same object in memory. It is therefore crucial to properly implement the `equals` method of values stored (no care needs to be taken in the case of `Mutable` which already comes with an `equals` implementation). This also potentially worsens RAM and harddisk usage, although it decreases the number of harddisk reads and avoid having to maintain an in-memory identifier set. A future version will likely to change this to keeping such an in-memory id set and persisting the `Mutable` only once, storing its id instead in each reference to it.
 - the API could be released separately from the implementations. Especially the database implementation should be abstracted away, so that it will be easier to try other key-value stores (and compare performances, for example). Also the database implementation currently doesn't do any sort of caching beyond what BDB does on its own. It might be useful to wrap `BerkeleyDB` in a `CachedSys` at some point. Preliminary tests yield around a 10x overhead of using the database STM compared to in-memory STM.

## creating an IntelliJ IDEA project

To develop the sources of LucreSTM, we recommend IntelliJ IDEA. If you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

Then to create the IDEA project, run the following two commands from the xsbt shell:

    > set ideaProjectName := "LucreSTM"
    > gen-idea

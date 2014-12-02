# LucreSTM

## statement

LucreSTM provides a software transactional memory with persistent backend for the Scala programming language. The STM is basically a wrapper around Scala-STM, but with an API which supports custom persistence and which can be extended to support confluent and quasi-retroactive persistent as implemented by the [TemporalObjects](https://github.com/Sciss/TemporalObjects) project.

LucreSTM is (C)opyright 2011&ndash;2014 by Hanns Holger Rutz. All rights reserved. The `core` module is released under the [GNU Lesser General Public License](https://raw.github.com/Sciss/LucreSTM/master/licenses/LucreSTM-Core-License.txt), whereas the `bdb` backend module for Berkeley DB JE 5 (itself governed by the Sleepycat License) is released under the [GNU General Public License v2+](https://raw.github.com/Sciss/LucreSTM/master/licenses/LucreSTM-BDB-License.txt), and the `bdb6` backend module for Berkeley DB JE 6 (itself governed by the AGPL 3 License) is released under the [GNU General Public License v3+](https://raw.github.com/Sciss/LucreSTM/master/licenses/LucreSTM-BDB6-License.txt). The software comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`

Further reading:

 - Rutz, H.H., "A Reactive, Confluently Persistent Framework for the Design of Computer Music Systems," in Proceedings of the 9th Sound and Music Computing Conference (SMC), Copenhagen 2012.
 - Bronson, N.G. and Chafi, H. and Olukotun, K., "CCSTM: A library-based STM for Scala," in Proceedings of the First Scala Workshop, 2010.

## requirements / installation

LucreSTM builds with sbt 0.12 against Scala 2.11, 2.10. It depends on [Scala-STM](http://nbronson.github.com/scala-stm/) 0.7.

## linking to LucreSTM

LucreSTM comes with three modules, `core`, `bdb` and `bdb6`, where the latter two depends on the former. The `bdb` module adds a durable store implementation based on Oracle BerkeleyDB Java Edition 5, `bdb6` uses BDB JE 6.

The following dependency is necessary:

    "de.sciss" %% "lucrestm" % v

Or just for the core module:

    "de.sciss" %% "lucrestm-core" % v

And for the database backend:

    resolvers += "Oracle Repository" at "http://download.oracle.com/maven"
    
    "de.sciss" %% "lucrestm-bdb"  % v   // BDB JE v5
    "de.sciss" %% "lucrestm-bdb6" % v   // BDB JE v6
    
Note that the file format of BDB JE v6 is not backward compatible with v5. Also BDB JE v6 requires Java 1.7, whereas BDB v5 works with Java 1.6.

The current version `v` is `"2.1.1`".

## documentation

At the moment, there are only sparse scala-docs, I'm afraid (run `sbt doc`). The basic concept:

An STM implementations uses the trait `Sys` which defines the actual transaction `Tx` and reference `Var` types, as well as identifiers `ID` which are used for persistence, and an access type `Acc` which is used by the confluent implementation. Actual implementations are in package `de.sciss.lucre.stm.impl`, they include a ephemeral but persisted database STM `BerkeleyDB`, an ephemeral in-memory implementation `InMemory`, and a confluent implementation `Confluent` which is not optimised or persisted to disk, but merely included as a proof of concept. (A fully fledged confluent STM is in project TemporalObjects).

To spawn a transaction, you need a `Cursor`. All of the systems included here mixin the `Cursor` trait, which provides the `step` method to open a transaction. Unlike Scala-STM, references can only be created within a transaction, thus their constructors are methods in `S#Tx` (which is a sub type of trait `Txn`). The underlying Scala-STM transaction can be read via `tx.peer`. LucreSTM currently does not implement any protocol system such as SBinary. It thus asks for readers and writers (both which combine into serializers) when constructing or reading in a reference.

The __life cycle__ of a reference, in following just called Var, is as follows: It comes into existence through `tx.newVar` (or one of the specialised methods such as `tx.newIntVar`). Apart from the initial value, this call requires to pass in an `S#ID` _parent_ identifier and a `Serializer` for the type of Var. The parent ID is the ID of the mutable objects in which the Var will reside. You can think of the ID as an opaque object, behind the scenes this is used by the confluent system to retrieve the access path of the parent. When you instantiate a new mutable object with transactional semantics, you can get a new ID by calling `tx.newID()`.

The serializer has a non-transactional `write` method, and a transactional `read` method:

```scala

    trait Serializer[-Txn, -Access, A] {
      def write(v: A, out: DataOutput): Unit
      def read(in: DataInput, access: Access)(implicit tx: Txn): A
    }
```

Again, the `Access` type (corresponding to `S#Acc`) is used by the confluent system, and you can treat it as an opaque entity given to you. As you can see, the `write` method does not have access to any transactional context, while the de-serialization requires the implicit `tx` argument as you might be dealing with a composite object whose members will be restored by successive calls to `tx.readVar`. Standard serializers are provided for primitive types and common Scala types such as `Option`, `Either`, `Tuple2`, ..., and various collections from `scala.collection.immutable`.

Continuing in the life cycle, once you have create a Var, you can read and write values to it using the `get` and `set` methods which, like a normal Scala-STM Ref, require an implicit transaction context.

Finally, objects must be disposed. We require explicit garbage disposal instead of superimposing something like weak reference sets. Therefore, it is necessary to call `dispose` on refs once they are not used any more. This requires some thinking about their visibility, but should improve performance compared to automatic garbage collection (also, GC would imply that the system initiates transactions itself, something considered highly problematic).

### Example

This is taken from the test sources. For conciseness, disposal is not demonstrated. Note how we use `Mutable` and `MutableSerializer` to minimise implementation costs. `Mutable.Impl` makes sure we have an `id` field, it also provides a `write` method which writes out that id and then calls `writeData`, similarly with `dispose` and `disposeData`. More importantly, it provides `hashCode` and `equals` based on the identifier. `MutableSerializer` has a convenient `write` method implementation, and reads the identifier, passing it into the only method left to implement, `readData`.

```scala

    import de.sciss.lucre._
    import de.sciss.serial._
    import stm.{Durable => S, _}

    object Person {
      implicit object ser extends MutableSerializer[S, Person] {
        def readData(in: DataInput, _id: S#ID)(implicit tx: S#Tx): Person = new Person with Mutable.Impl[S] {
          val id      = _id
          val name    = in.readString()
          val friends = tx.readVar[List[Person]](id, in)
        }
      }
    }
    trait Person extends Mutable[S] {
      def name: String
      def friends: S#Var[List[Person]]
      protected def disposeData()(implicit tx: S#Tx): Unit = friends.dispose()
      protected def writeData(out: DataOutput): Unit = {
        out.writeString(name)
        friends.write(out)
      }
    }

    val pre  = IndexedSeq("Adal", "Bern", "Chlod", "Diet", "Eg",   "Fried")
    val post = IndexedSeq("bert", "hard", "wig",   "mar",  "mund", "helm" )
    val rnd  = new util.Random()

    // create a person with random name and no friends
    def newPerson()(implicit tx: S#Tx): Person = new Person with Mutable.Impl[S] {
      val id      = tx.newID()
      val name    = pre(rnd.nextInt(pre.size)) + post(rnd.nextInt(post.size))
      val friends = tx.newVar[List[Person]](id, Nil)
    }

    val dir  = new java.io.File(sys.props("user.home"), "person_db")
    dir.mkdirs()
    val s    = S(store.BerkeleyDB.open(dir))
    // read the root data set, or create a new one if the database does not exist
    val root = s.root { implicit tx => newPerson() }

    def gather(p: Person, set: Set[Person])(implicit tx: S#Tx): Set[Person] = {
      if (!set.contains(p)) {
        val set1 = set + p
        p.friends().foldLeft(set1) { (s2, p1) => gather(p1, s2) }
      } else set
    }

    // see who is in the database so far
    val found = s.step { implicit tx => gather(root(), Set.empty) }
    val infos = s.step { implicit tx => found.map { p =>
      "Remember " + p.name + "? He's " + (p.friends() match {
        case Nil => "lonely"
        case fs  => fs.map(_.name).mkString("friend of ", " and ", "")
      })
    }}
    infos foreach println

    // create a new person and make it friend of half of the population
    s.step { implicit tx =>
      val p = newPerson()
      val friends0 = found.filter(_ => rnd.nextBoolean())
      val friends  = if (friends0.isEmpty) Seq(root()) else friends0
      friends.foreach { f =>
        p.friends.transform(f :: _)
        f.friends.transform(p :: _)
      }
      println(s"Say hi to ${p.name}. He's friend of ${friends.map(_.name).mkString(" and ")}")
    }
```

Now re-run the program to verify the persons have been persisted.

## limitations, future ideas

 - This project is not yet optimized for best possible performance, but rather for simplicity!
 - Currently all persisted types need explicit serializers, which can be tiresome. The idea was that this way we can have very fast and efficient serialization, but probably it would be helpful if we could use standard Java serialization (`@serializable`) as a default layer in the absence of explicit serializers.
 - Currently the fields of references are directly written out in the database version. This can yield to several representations of the same object in memory. It is therefore crucial to properly implement the `equals` method of values stored (no care needs to be taken in the case of `Mutable.Impl` which already comes with an `equals` implementation). This also potentially worsens RAM and hard-disk usage, although it decreases the number of hard-disk reads and avoid having to maintain an in-memory identifier set. A future version will likely to change this to keeping such an in-memory id set and persisting the `Mutable` only once, storing its id instead in each reference to it.
 - the API could be released separately from the implementations. Especially the database implementation should be abstracted away, so that it will be easier to try other key-value stores (and compare performances, for example). Also the database implementation currently doesn't do any sort of caching beyond what BDB does on its own. It might be useful to wrap `BerkeleyDB` in a `CachedSys` at some point. Preliminary tests yield around a 10x overhead of using the database STM compared to in-memory STM.

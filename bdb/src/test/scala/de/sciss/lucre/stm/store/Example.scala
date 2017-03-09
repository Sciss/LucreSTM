package de.sciss
package lucre
package stm
package store

import de.sciss.serial.{DataInput, DataOutput}

object Example extends App {
   import stm.{Durable => S}

//  showLog = true

  object Person {
    implicit object ser extends MutableSerializer[S, Person] {
      def readData(in: DataInput, _id: S#ID)(implicit tx: S#Tx): Person = new Person with Mutable.Impl[S] {
        val id      = _id
        val name    = in.readUTF()
        val friends = tx.readVar[ List[ Person ]]( id, in )
      }
    }
  }

  trait Person extends Mutable[S#ID, S#Tx] {
    def name: String

    def friends: S#Var[List[Person]]

    protected def disposeData()(implicit tx: S#Tx): Unit = friends.dispose()

    protected def writeData(out: DataOutput): Unit = {
      out.writeUTF(name)
      friends.write(out)
    }
  }

  val pre  = IndexedSeq("Adal", "Bern", "Chlod", "Diet", "Eg"  , "Fried")
  val post = IndexedSeq("bert", "hard", "wig"  , "mar" , "mund", "helm" )
  val rnd  = new util.Random()

  // create a person with random name and no friends
  def newPerson()(implicit tx: S#Tx): Person = new Person with Mutable.Impl[S] {
    val id      = tx.newID()
    val name    = pre(rnd.nextInt(pre.size)) + post(rnd.nextInt(post.size))
    val friends = tx.newVar[List[Person]](id, Nil)
  }

  val dir = new java.io.File(sys.props("user.home"), "person_db")
  dir.mkdirs()
  val s = S(BerkeleyDB.open(dir))
  // read the root data set, or create a new one if the database does not exist
  val root = s.root { implicit tx => newPerson() }

  def gather(p: Person, set: Set[Person])(implicit tx: S#Tx): Set[Person] =
    if (!set.contains(p)) {
      val set1 = set + p
      p.friends().foldLeft(set1)((s2, p1) => gather(p1, s2))
    } else set

   // see who is in the database so far
  val found = s.step { implicit tx => gather(root(), Set.empty) }
  val infos = s.step { implicit tx => found.map { p =>
    val state = p.friends() match {
      case Nil => "lonely"
      case fs  => fs.map(_.name).mkString("friend of ", " and ", "")
    }
    s"Remember ${p.name}? He's $state"
  }}
  infos.foreach(println)

  // create a new person and make it friend of half of the population
  s.step { implicit tx =>
    val p = newPerson()
    val friends0 = found.filter(_ => rnd.nextBoolean())
    val friends = if (friends0.isEmpty) Seq(root()) else friends0
    friends.foreach { f =>
      p.friends.transform(f :: _)
      f.friends.transform(p :: _)
    }
    println(s"Say hi to ${p.name}. He's friend of ${friends.map(_.name).mkString(" and ")}")
  }

  // now re-run the program to verify the persons have been persisted
}

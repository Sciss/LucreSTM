## LucreSTM

### statement

LucreSTM is a thin wrapper around Scala-STM, providing both a pure in-memory STM and a database backed persistent STM, using the same API.

LucreSTM is (C)opyright 2011 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](https://raw.github.com/Sciss/LucreSTM/master/licenses/LucreSTM-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`

### requirements / installation

LucreSTM builds with sbt 0.11 against Scala 2.9.1. It depends on Scala-STM 0.4 and currently uses Berkeley DB JE 4.10 as the database backend.

### documentation

At the moment, there are only sparse scaladocs, I'm afraid (run `sbt doc`). The basic concept:

 - Refs use a subset of operations provided by Scala-STM Refs, and adds stuff necessary for persisting them to harddisk.
 - In particular, we require explicit garbage disposal instead of superimposing something like weak reference sets. Therefore, it is necessary to call `dispose` on refs once they are not used any more. This requires some thinking about their visibility, but should improve performance compared to automatic garbage collection (also, GC would imply that the system initiates transactions itself, something considered highly problematic).
 - We distinguish between Vals and Refs. Vals are mutable transactional fields pointing to immutable objects. Refs are pointing to mutable objects. These have to mix in trait `Mutable` so as to implement a system specific identifier (`id`) as well as disposal (`disposeData`) and serialization (`writeData`).
 - We also tentatively support the creation of Option-Refs, further restricting the type stored in the Ref. The idea is to create flat objects for "None" and "Some" without the need to wrap them actually. Not sure this pays of really.

In the future, the API could be released separately, but currently it is published along with two implementations, `InMemory` (just using Scala-STM) and `BerkeleyDB`, using Berkeley DB Java Edition. Especially the latter should be abstracted away, so that it will be easier to try other key-value stores (and compare performances, for example). Also the database implementation currently doesn't do any sort of caching beyond what BDB does on its own. It might be useful to wrap `BerkeleyDB` in a `CachedSys` at some point. Preliminary tests yield around a 10x overhead of using the database STM compared to in-memory STM.

### limitations, future ideas

 - This project is in early test stage, so it is not yet optimized for best possible performance, but rather for simplicity!
 - It might be useful to have `S#Tx` carry the system itself, instead of requiring each mutable data structure to keep a reference to the underlying system itself. This would save memory, I guess
 - Currently all persisted types need explicit serializers, which can be tiresome. The idea was that this way we can have very fast and efficient serialization, but probably it would be helpful if we could use standard Java serialization (`@serializable`) as a default layer in the absence of explicit serializers.
 - Currently the fields of Vals and Refs are directly written out in the database version. This can yield to several representations of the same object in memory. It is therefore crucial to properly implement the `equals` method of values stored (no care needs to be taken in the case of `Mutable` which already comes with an `equals` implementation). This also potentially worsens RAM and harddisk usage, although it decreases the number of harddisk reads and avoid having to maintain an in-memory identifier set. A future version will likely to change this to keeping such an in-memory id set and persisting the `Mutable` only once, storing its id instead in each reference to it.

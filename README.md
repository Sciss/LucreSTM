## LucreSTM

### statement

LucreSTM is a thin wrapper around Scala-STM, providing both a pure in-memory STM and a database backed persistent STM, using the same API.

LucreSTM is (C)opyright 2011 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](https://raw.github.com/Sciss/LucreSTM/master/licenses/LucreSTM-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`

### requirements / installation

LucreSTM builds with sbt 0.11 against Scala 2.9.1. It depends on Scala-STM 0.4 and currently uses Berkeley DB JE 4.10 as the database backend.

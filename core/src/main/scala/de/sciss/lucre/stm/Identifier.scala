/*
 *  Identifier.scala
 *  (LucreSTM-Core)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package lucre
package stm

trait Identifier[-Tx] extends Disposable[Tx] with serial.Writable
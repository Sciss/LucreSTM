/*
 *  package.scala
 *  (LucreSTM)
 *
 *  Copyright (c) 2011-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre

import java.text.SimpleDateFormat
import java.util.{Date, Locale}
import annotation.elidable
import annotation.elidable.CONFIG

package object stm {
  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'Lucre' - 'stm' ", Locale.US)
  var showLog = false

  @elidable(CONFIG) private[lucre] def log(what: => String): Unit =
    if (showLog) println(logHeader.format(new Date()) + what)

  /** Specialization group consisting of all specializable types except `Byte` and `Short`.
    *
    * (AnyRef specialization seems currently disabled in Scala)
    */
  val SpecGroup = new Specializable.Group((
      scala.Int,  scala.Long,    scala.Float, scala.Double,
      scala.Char, scala.Boolean, scala.Unit /* , scala.AnyRef */
    ))
}

package scalan.primitives

import scalan.{ScalanExp}

trait ExceptionsExp { self: ScalanExp =>
  case class ThrowException(msg: Rep[String]) extends BaseDef[Unit]
  def THROW(msg: Rep[String]): Rep[Unit] = ThrowException(msg)    
}

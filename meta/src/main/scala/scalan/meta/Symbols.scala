package scalan.meta

import java.util.Objects

import Symbols._

trait Symbols { this: AstContext =>

  def newUnitSymbol(packageName: String, name: String): SUnitDefSymbol = {
    SUnitDefSymbol(SNoSymbol, SName(packageName, name))
  }

  def newEntitySymbol(owner: SSymbol, name: String): SNamedDefSymbol = {
    SEntityDefSymbol(owner, name)
  }

  def newEntityItemSymbol(owner: SSymbol, name: String, defType: DefType.Value): SNamedDefSymbol = {
    SEntityItemSymbol(owner, name, defType)
  }

}

object Symbols {

  sealed trait SSymbol {
    def owner: SSymbol
    def outerUnit: SUnitDefSymbol = this match {
      case us: SUnitDefSymbol =>
        assert(us.owner eq SNoSymbol, s"UnitSymbol have owner ${us.owner}")
        us
      case _ =>
        assert(owner ne SNoSymbol, s"$this doesn't have owner")
        owner.outerUnit
    }
    def fullName: String
    override def toString: String = s"sym://$fullName"
  }

  object DefType extends Enumeration {
    val Unit, Entity, Def, Val, ClassArg, Type = Value
  }

  trait SNamedDefSymbol extends SSymbol {
    def name: String
    def defType: DefType.Value
    def fullName: String = s"${owner.fullName}.$name"
  }

  case object SNoSymbol extends SSymbol {
    override def owner: SSymbol = this
    override def fullName: String = "NoSym"
  }

  trait SEntitySymbol extends SNamedDefSymbol {
  }

  case class SEntityDefSymbol private(owner: SSymbol, name: String)
      extends SEntitySymbol {
    def defType = DefType.Entity
  }

  case class SEntityItemSymbol private(owner: SSymbol, name: String, defType: DefType.Value)
      extends SNamedDefSymbol {
  }

  case class SUnitDefSymbol private(override val owner: SSymbol, unitName: SName)
      extends SEntitySymbol {
    def name = unitName.mkFullName
    override def defType = DefType.Unit
    
    override def fullName: String =
      if (owner eq SNoSymbol) unitName.mkFullName
      else super.fullName
  }

}

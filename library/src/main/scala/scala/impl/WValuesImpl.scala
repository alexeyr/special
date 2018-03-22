package scala

import scalan._
import impl._
import scala.wrappers.WrappersModule
import scala.reflect.runtime.universe._
import scala.reflect._

package impl {
// Abs -----------------------------------
trait WValuesDefs extends scalan.Scalan with WValues {
  self: WrappersModule =>

  // entityProxy: single proxy for each type family
  implicit def proxyWValue(p: Rep[WValue]): WValue = {
    proxyOps[WValue](p)(scala.reflect.classTag[WValue])
  }

  // familyElem
  class WValueElem[To <: WValue]
    extends EntityElem[To] {
    lazy val parent: Option[Elem[_]] = None
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs()
    override lazy val tag = {
      weakTypeTag[WValue].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[WValue] => convertWValue(x) }
      tryConvert(element[WValue], this, x, conv)
    }

    def convertWValue(x: Rep[WValue]): Rep[To] = {
      x.elem match {
        case _: WValueElem[_] => x.asRep[To]
        case e => !!!(s"Expected $x to have WValueElem[_], but got $e", x)
      }
    }
    override def getDefaultRep: Rep[To] = ???
  }

  implicit def wValueElement: Elem[WValue] =
    cachedElem[WValueElem[WValue]]()

  implicit case object WValueCompanionElem extends CompanionElem[WValueCompanionCtor] {
    lazy val tag = weakTypeTag[WValueCompanionCtor]
    protected def getDefaultRep = WValue
  }

  abstract class WValueCompanionCtor extends CompanionDef[WValueCompanionCtor] with WValueCompanion {
    def selfType = WValueCompanionElem
    override def toString = "WValue"
  }
  implicit def proxyWValueCompanionCtor(p: Rep[WValueCompanionCtor]): WValueCompanionCtor =
    proxyOps[WValueCompanionCtor](p)

  lazy val WValue: Rep[WValueCompanionCtor] = new WValueCompanionCtor {
  }

  object WValueMethods {
  }

  object WValueCompanionMethods {
  }

  registerModule(WValuesModule)
}

object WValuesModule extends scalan.ModuleInfo("scala", "WValues")
}

trait WValuesModule extends scala.impl.WValuesDefs {self: WrappersModule =>}

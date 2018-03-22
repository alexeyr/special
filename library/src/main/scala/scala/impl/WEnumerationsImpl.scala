package scala

import scalan._
import impl._
import scala.wrappers.WrappersModule
import scala.reflect.runtime.universe._
import scala.reflect._

package impl {
// Abs -----------------------------------
trait WEnumerationsDefs extends scalan.Scalan with WEnumerations {
  self: WrappersModule =>

  // entityProxy: single proxy for each type family
  implicit def proxyWEnumeration(p: Rep[WEnumeration]): WEnumeration = {
    proxyOps[WEnumeration](p)(scala.reflect.classTag[WEnumeration])
  }

  // familyElem
  class WEnumerationElem[To <: WEnumeration]
    extends EntityElem[To] {
    lazy val parent: Option[Elem[_]] = None
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs()
    override lazy val tag = {
      weakTypeTag[WEnumeration].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[WEnumeration] => convertWEnumeration(x) }
      tryConvert(element[WEnumeration], this, x, conv)
    }

    def convertWEnumeration(x: Rep[WEnumeration]): Rep[To] = {
      x.elem match {
        case _: WEnumerationElem[_] => x.asRep[To]
        case e => !!!(s"Expected $x to have WEnumerationElem[_], but got $e", x)
      }
    }
    override def getDefaultRep: Rep[To] = ???
  }

  implicit def wEnumerationElement: Elem[WEnumeration] =
    cachedElem[WEnumerationElem[WEnumeration]]()

  implicit case object WEnumerationCompanionElem extends CompanionElem[WEnumerationCompanionCtor] {
    lazy val tag = weakTypeTag[WEnumerationCompanionCtor]
    protected def getDefaultRep = WEnumeration
  }

  abstract class WEnumerationCompanionCtor extends CompanionDef[WEnumerationCompanionCtor] with WEnumerationCompanion {
    def selfType = WEnumerationCompanionElem
    override def toString = "WEnumeration"
  }
  implicit def proxyWEnumerationCompanionCtor(p: Rep[WEnumerationCompanionCtor]): WEnumerationCompanionCtor =
    proxyOps[WEnumerationCompanionCtor](p)

  lazy val WEnumeration: Rep[WEnumerationCompanionCtor] = new WEnumerationCompanionCtor {
  }

  object WEnumerationMethods {
  }

  object WEnumerationCompanionMethods {
  }

  registerModule(WEnumerationsModule)
}

object WEnumerationsModule extends scalan.ModuleInfo("scala", "WEnumerations")
}

trait WEnumerationsModule extends scala.impl.WEnumerationsDefs {self: WrappersModule =>}

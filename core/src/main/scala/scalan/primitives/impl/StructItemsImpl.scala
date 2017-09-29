package scalan.primitives

import scala.annotation.unchecked.uncheckedVariance
import scalan._
import scala.reflect.runtime.universe._
import scalan.common.OverloadHack.{Overloaded2, Overloaded1}
import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeTag}
import scalan.meta.ScalanAst._

package impl {
// Abs -----------------------------------
trait StructItemsDefs extends StructItems {
  self: Structs with Scalan =>

  // entityProxy: single proxy for each type family
  implicit def proxyStructItem[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]): StructItem[Val, Schema] = {
    proxyOps[StructItem[Val, Schema]](p)(scala.reflect.classTag[StructItem[Val, Schema]])
  }

  // familyElem
  class StructItemElem[Val, Schema <: Struct, To <: StructItem[Val, Schema]](implicit _eVal: Elem[Val @uncheckedVariance], _eSchema: Elem[Schema])
    extends EntityElem[To] {
    def eVal = _eVal
    def eSchema = _eSchema
    lazy val parent: Option[Elem[_]] = None
    lazy val typeArgs = TypeArgs("Val" -> (eVal -> scalan.util.Covariant), "Schema" -> (eSchema -> scalan.util.Invariant))
    override lazy val tag = {
      implicit val tagAnnotatedVal = eVal.tag
      implicit val tagSchema = eSchema.tag
      weakTypeTag[StructItem[Val, Schema]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[StructItem[Val, Schema]] => convertStructItem(x) }
      tryConvert(element[StructItem[Val, Schema]], this, x, conv)
    }

    def convertStructItem(x: Rep[StructItem[Val, Schema]]): Rep[To] = {
      x.elem match {
        case _: StructItemElem[_, _, _] => x.asRep[To]
        case e => !!!(s"Expected $x to have StructItemElem[_, _, _], but got $e", x)
      }
    }

    override def getDefaultRep: Rep[To] = ???
  }

  implicit def structItemElement[Val, Schema <: Struct](implicit eVal: Elem[Val @uncheckedVariance], eSchema: Elem[Schema]): Elem[StructItem[Val, Schema]] =
    cachedElem[StructItemElem[Val, Schema, StructItem[Val, Schema]]](eVal, eSchema)

  implicit case object StructItemCompanionElem extends CompanionElem[StructItemCompanionCtor] {
    lazy val tag = weakTypeTag[StructItemCompanionCtor]
    protected def getDefaultRep = StructItem
  }

  abstract class StructItemCompanionCtor extends CompanionDef[StructItemCompanionCtor] {
    def selfType = StructItemCompanionElem
    override def toString = "StructItem"
  }
  implicit def proxyStructItemCompanionCtor(p: Rep[StructItemCompanionCtor]): StructItemCompanionCtor =
    proxyOps[StructItemCompanionCtor](p)

  case class StructItemBaseCtor[Val, Schema <: Struct]
      (override val key: Rep[StructKey[Schema]], override val value: Rep[Val])
    extends StructItemBase[Val, Schema](key, value) with Def[StructItemBase[Val, Schema]] {
    implicit val eVal = value.elem;
implicit val eSchema = key.elem.typeArgs("Schema")._1.asElem[Schema]
    lazy val selfType = element[StructItemBase[Val, Schema]]
  }
  // elem for concrete class
  class StructItemBaseElem[Val, Schema <: Struct](val iso: Iso[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]])(implicit override val eVal: Elem[Val], override val eSchema: Elem[Schema])
    extends StructItemElem[Val, Schema, StructItemBase[Val, Schema]]
    with ConcreteElem[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]] {
    override lazy val parent: Option[Elem[_]] = Some(structItemElement(element[Val], element[Schema]))
    override lazy val typeArgs = TypeArgs("Val" -> (eVal -> scalan.util.Invariant), "Schema" -> (eSchema -> scalan.util.Invariant))

    override def convertStructItem(x: Rep[StructItem[Val, Schema]]) = StructItemBase(x.key, x.value)
    override def getDefaultRep = StructItemBase(element[StructKey[Schema]].defaultRepValue, element[Val].defaultRepValue)
    override lazy val tag = {
      implicit val tagVal = eVal.tag
      implicit val tagSchema = eSchema.tag
      weakTypeTag[StructItemBase[Val, Schema]]
    }
  }

  // state representation type
  type StructItemBaseData[Val, Schema <: Struct] = (StructKey[Schema], Val)

  // 3) Iso for concrete class
  class StructItemBaseIso[Val, Schema <: Struct](implicit eVal: Elem[Val], eSchema: Elem[Schema])
    extends EntityIso[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]] with Def[StructItemBaseIso[Val, Schema]] {
    override def from(p: Rep[StructItemBase[Val, Schema]]) =
      (p.key, p.value)
    override def to(p: Rep[(StructKey[Schema], Val)]) = {
      val Pair(key, value) = p
      StructItemBase(key, value)
    }
    lazy val eFrom = pairElement(element[StructKey[Schema]], element[Val])
    lazy val eTo = new StructItemBaseElem[Val, Schema](self)
    lazy val selfType = new StructItemBaseIsoElem[Val, Schema](eVal, eSchema)
    def productArity = 2
    def productElement(n: Int) = n match {
      case 0 => eVal
      case 1 => eSchema
    }
  }
  case class StructItemBaseIsoElem[Val, Schema <: Struct](eVal: Elem[Val], eSchema: Elem[Schema]) extends Elem[StructItemBaseIso[Val, Schema]] {
    def getDefaultRep = reifyObject(new StructItemBaseIso[Val, Schema]()(eVal, eSchema))
    lazy val tag = {
      implicit val tagVal = eVal.tag
      implicit val tagSchema = eSchema.tag
      weakTypeTag[StructItemBaseIso[Val, Schema]]
    }
    lazy val typeArgs = TypeArgs("Val" -> (eVal -> scalan.util.Invariant), "Schema" -> (eSchema -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class StructItemBaseCompanionCtor extends CompanionDef[StructItemBaseCompanionCtor] {
    def selfType = StructItemBaseCompanionElem
    override def toString = "StructItemBaseCompanion"
    @scalan.OverloadId("fromData")
    def apply[Val, Schema <: Struct](p: Rep[StructItemBaseData[Val, Schema]]): Rep[StructItemBase[Val, Schema]] = {
      implicit val eVal = p._2.elem;
implicit val eSchema = p._1.elem.typeArgs("Schema")._1.asElem[Schema]
      isoStructItemBase[Val, Schema].to(p)
    }

    @scalan.OverloadId("fromFields")
    def apply[Val, Schema <: Struct](key: Rep[StructKey[Schema]], value: Rep[Val]): Rep[StructItemBase[Val, Schema]] =
      mkStructItemBase(key, value)

    def unapply[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]) = unmkStructItemBase(p)
  }
  lazy val StructItemBaseRep: Rep[StructItemBaseCompanionCtor] = new StructItemBaseCompanionCtor
  lazy val StructItemBase: StructItemBaseCompanionCtor = proxyStructItemBaseCompanion(StructItemBaseRep)
  implicit def proxyStructItemBaseCompanion(p: Rep[StructItemBaseCompanionCtor]): StructItemBaseCompanionCtor = {
    proxyOps[StructItemBaseCompanionCtor](p)
  }

  implicit case object StructItemBaseCompanionElem extends CompanionElem[StructItemBaseCompanionCtor] {
    lazy val tag = weakTypeTag[StructItemBaseCompanionCtor]
    protected def getDefaultRep = StructItemBaseRep
  }

  implicit def proxyStructItemBase[Val, Schema <: Struct](p: Rep[StructItemBase[Val, Schema]]): StructItemBase[Val, Schema] =
    proxyOps[StructItemBase[Val, Schema]](p)

  implicit class ExtendedStructItemBase[Val, Schema <: Struct](p: Rep[StructItemBase[Val, Schema]]) {
    def toData: Rep[StructItemBaseData[Val, Schema]] = {
      implicit val eVal = p.value.elem;
implicit val eSchema = p.key.elem.typeArgs("Schema")._1.asElem[Schema]
      isoStructItemBase(eVal, eSchema).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoStructItemBase[Val, Schema <: Struct](implicit eVal: Elem[Val], eSchema: Elem[Schema]): Iso[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]] =
    reifyObject(new StructItemBaseIso[Val, Schema]()(eVal, eSchema))

  registerModule(StructItemsModule)

  lazy val StructItem: Rep[StructItemCompanionCtor] = new StructItemCompanionCtor {
  }

  object StructItemBaseMethods {
  }

  def mkStructItemBase[Val, Schema <: Struct]
    (key: Rep[StructKey[Schema]], value: Rep[Val]): Rep[StructItemBase[Val, Schema]] = {
    new StructItemBaseCtor[Val, Schema](key, value)
  }
  def unmkStructItemBase[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: StructItemBaseElem[Val, Schema] @unchecked =>
      Some((p.asRep[StructItemBase[Val, Schema]].key, p.asRep[StructItemBase[Val, Schema]].value))
    case _ =>
      None
  }

  object StructItemMethods {
    object key {
      def unapply(d: Def[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[StructItemElem[_, _, _]] && method.getName == "key" =>
          Some(receiver).asInstanceOf[Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object value {
      def unapply(d: Def[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[StructItemElem[_, _, _]] && method.getName == "value" =>
          Some(receiver).asInstanceOf[Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }
}

object StructItemsModule extends scalan.ModuleInfo {
  val dump = "H4sIAAAAAAAAALVWXWgcVRS+O7vpZjfpT6KpWBXTZIpay271pUIQSdJEatckdEqUNSh3Z262t5mf6527cVZKwZc+WHwpPgk+FARBgiJ5KSJSFEF86Lv0rVIQpCJ9MChYPPfO726yG/PQfRhm7t7znXO+77tnZuMeGvA5GvdNbGO34hCBK4a6n/aFbrzuWS2bnCard7zN0WtX//5CQ4fqaD/1lykXLWzT94lVR2Pe2pxDxSKnzTDgPMdU1ND+OVdQ0dYdtSjQ8VqYpirTVHdKo0cRUzU0er7NiNF2PZc6CUJ1d4RsGMA8+gbHjBHeVcoLuwN1BgJUCbsm8YXHfYGOhvFV07NtYgrquVXqOC2BGzap1qgvYP8h03NNTgQxZm3s+8R/F11GhRoaJBKSJs8l9dxeZCnu9roUpVCWxA33nyNM9dl2BDoQlbPIZCmwp0gd5nERpygC3AXPih8LLoYFNFq7iNdxFVI0q4bg1G1C5EGvU0YZsq+Ghhg213CTLECkXCpCHz6xVyXdakvAcogxBmZ6UdVSSampJNRUJDW6QTiV3sHyzyXuBW0U/nJ5hAIJcWIXiBiBzLmW/uGK+daWMeRoMjhQPZYA4+kenlZiAJM/nbvm33/1+ikNleuoTP3phi84NkVW6IivIey6nlDlJhRi3gS9JnrppbJMwx6gtNDwrHYstuk5DLuAFBE7DErZ1KRCbpZrByN9dmQZpBSMxFsLQHrSb68zLGOnGbPbP1z67tKvT/4yoqG8NGHAeAY2D7B92lFWmMW2De1oIk4OWcuhUobnkJGJ+/Tt61eFhnI1lAs6/bXYuAhKTgUcDYcRoVUf0FP/3j6wKrRI+J5NxPm/Ld78/re7rxQ0pHXyVIIGjDloKi5OoDKYumWKM4I4EUvy+rhA+WVsy9tyvLDPMC8QB6s1eRkO5PVI13O5T4GJ3s/8/of140m0olhQLonJ+l/GBIjRlz755hhZ+lJDg3V1kOdt3FQWlXqdJr5ZR4PeOuHhenEd2/JuR5sWLbKKW3Z8irOEhYqP91ScEcnmVMDkwYzbHwrFW/Bcos8v6X8ZP3+8IbWT/z8GxK6RdkIyDKhO2kuhHmejPaX+7A+HR1lP0svLswINQL8t0jtLIm4WKgVQ245mQvRcV4ICAYQYrTBn93RPtspukCIJm+qD06ttGOepcWewT9K6pX2e6nVE1Fz46p+ZzyePPPFAQ8XX0MAq+MLf0RgDDa/lWvEMgjeVIIGYidcKnVaBmYM5dpIX2DqG8QszUqDDsX1agtrV5Wg9NA38xlHKibrLnj2ODketyODKGTeEFfrzNzbeo7eemw+Hgtx8tgNOLY2lNCqykkTl7cwubXNVv3MYv2q/vnJl7M/P3nlEvSAGG1Q4mOkn9/B6iKf5Qxz/KGOp0U5e8tBCp+G2z0OOJvu4KeyKWJObH9x7uXHiIzVzB1Sz6QxRt8fkuB1puUC7uUas2Ad7nLfhdWXb1FUnNtVXualzvGdPSOqX3F7sN+vF9hPYuult3ag/HPvJ65tpRVHIUJc0MEFGouoYp/BxTdeJHyXmaKKHaEbkFVD+8tanC8dvbd5VmpWl62Bku8kHYWqxoGt8FsNK/G69ZJKMMjDRpDP/A8IzkB1ADAAA"
}
}


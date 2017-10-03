package scalan.common

import scalan.Scalan
import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeTag}
import scalan.meta.ScalanAst._

package impl {
// Abs -----------------------------------
trait MetaTestsDefs extends scalan.Scalan with MetaTests {
  self: MetaTestsModule =>

  // entityProxy: single proxy for each type family
  implicit def proxyMetaTest[T](p: Rep[MetaTest[T]]): MetaTest[T] = {
    proxyOps[MetaTest[T]](p)(scala.reflect.classTag[MetaTest[T]])
  }

  // familyElem
  class MetaTestElem[T, To <: MetaTest[T]](implicit _elem: Elem[T])
    extends EntityElem[To] {
    def elem = _elem
    lazy val parent: Option[Elem[_]] = None
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("T" -> (elem -> scalan.util.Invariant))
    override lazy val tag = {
      implicit val tagT = elem.tag
      weakTypeTag[MetaTest[T]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[MetaTest[T]] => convertMetaTest(x) }
      tryConvert(element[MetaTest[T]], this, x, conv)
    }

    def convertMetaTest(x: Rep[MetaTest[T]]): Rep[To] = {
      x.elem match {
        case _: MetaTestElem[_, _] => x.asRep[To]
        case e => !!!(s"Expected $x to have MetaTestElem[_, _], but got $e", x)
      }
    }

    override def getDefaultRep: Rep[To] = ???
  }

  implicit def metaTestElement[T](implicit elem: Elem[T]): Elem[MetaTest[T]] =
    cachedElem[MetaTestElem[T, MetaTest[T]]](elem)

  implicit case object MetaTestCompanionElem extends CompanionElem[MetaTestCompanionCtor] {
    lazy val tag = weakTypeTag[MetaTestCompanionCtor]
    protected def getDefaultRep = MetaTest
  }

  abstract class MetaTestCompanionCtor extends CompanionDef[MetaTestCompanionCtor] with MetaTestCompanion {
    def selfType = MetaTestCompanionElem
    override def toString = "MetaTest"
  }
  implicit def proxyMetaTestCompanionCtor(p: Rep[MetaTestCompanionCtor]): MetaTestCompanionCtor =
    proxyOps[MetaTestCompanionCtor](p)

  case class MT0Ctor
      (override val size: Rep[Int])
    extends MT0(size) with Def[MT0] {
    lazy val selfType = element[MT0]
  }
  // elem for concrete class
  class MT0Elem(val iso: Iso[MT0Data, MT0])
    extends MetaTestElem[Unit, MT0]
    with ConcreteElem[MT0Data, MT0] {
    override lazy val parent: Option[Elem[_]] = Some(metaTestElement(UnitElement))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs()

    override def convertMetaTest(x: Rep[MetaTest[Unit]]) = MT0(x.size)
    override def getDefaultRep = MT0(0)
    override lazy val tag = {
      weakTypeTag[MT0]
    }
  }

  // state representation type
  type MT0Data = Int

  // 3) Iso for concrete class
  class MT0Iso
    extends EntityIso[MT0Data, MT0] with Def[MT0Iso] {
    override def from(p: Rep[MT0]) =
      p.size
    override def to(p: Rep[Int]) = {
      val size = p
      MT0(size)
    }
    lazy val eFrom = element[Int]
    lazy val eTo = new MT0Elem(self)
    lazy val selfType = new MT0IsoElem
    def productArity = 0
    def productElement(n: Int) = ???
  }
  case class MT0IsoElem() extends Elem[MT0Iso] {
    def getDefaultRep = reifyObject(new MT0Iso())
    lazy val tag = {
      weakTypeTag[MT0Iso]
    }
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs()
  }
  // 4) constructor and deconstructor
  class MT0CompanionCtor extends CompanionDef[MT0CompanionCtor] with MT0Companion {
    def selfType = MT0CompanionElem
    override def toString = "MT0Companion"

    @scalan.OverloadId("fromFields")
    def apply(size: Rep[Int]): Rep[MT0] =
      mkMT0(size)

    def unapply(p: Rep[MetaTest[Unit]]) = unmkMT0(p)
  }
  lazy val MT0Rep: Rep[MT0CompanionCtor] = new MT0CompanionCtor
  lazy val MT0: MT0CompanionCtor = proxyMT0Companion(MT0Rep)
  implicit def proxyMT0Companion(p: Rep[MT0CompanionCtor]): MT0CompanionCtor = {
    proxyOps[MT0CompanionCtor](p)
  }

  implicit case object MT0CompanionElem extends CompanionElem[MT0CompanionCtor] {
    lazy val tag = weakTypeTag[MT0CompanionCtor]
    protected def getDefaultRep = MT0Rep
  }

  implicit def proxyMT0(p: Rep[MT0]): MT0 =
    proxyOps[MT0](p)

  implicit class ExtendedMT0(p: Rep[MT0]) {
    def toData: Rep[MT0Data] = {
      isoMT0.from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoMT0: Iso[MT0Data, MT0] =
    reifyObject(new MT0Iso())

  case class MT1Ctor[T]
      (override val data: Rep[T], override val size: Rep[Int])
    extends MT1[T](data, size) with Def[MT1[T]] {
    implicit val eT = data.elem
    lazy val selfType = element[MT1[T]]
  }
  // elem for concrete class
  class MT1Elem[T](val iso: Iso[MT1Data[T], MT1[T]])(implicit val eT: Elem[T])
    extends MetaTestElem[T, MT1[T]]
    with ConcreteElem[MT1Data[T], MT1[T]] {
    override lazy val parent: Option[Elem[_]] = Some(metaTestElement(element[T]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("T" -> (eT -> scalan.util.Invariant))

    override def convertMetaTest(x: Rep[MetaTest[T]]) = // Converter is not generated by meta
!!!("Cannot convert from MetaTest to MT1: missing fields List(data)")
    override def getDefaultRep = MT1(element[T].defaultRepValue, 0)
    override lazy val tag = {
      implicit val tagT = eT.tag
      weakTypeTag[MT1[T]]
    }
  }

  // state representation type
  type MT1Data[T] = (T, Int)

  // 3) Iso for concrete class
  class MT1Iso[T](implicit eT: Elem[T])
    extends EntityIso[MT1Data[T], MT1[T]] with Def[MT1Iso[T]] {
    override def from(p: Rep[MT1[T]]) =
      (p.data, p.size)
    override def to(p: Rep[(T, Int)]) = {
      val Pair(data, size) = p
      MT1(data, size)
    }
    lazy val eFrom = pairElement(element[T], element[Int])
    lazy val eTo = new MT1Elem[T](self)
    lazy val selfType = new MT1IsoElem[T](eT)
    def productArity = 1
    def productElement(n: Int) = eT
  }
  case class MT1IsoElem[T](eT: Elem[T]) extends Elem[MT1Iso[T]] {
    def getDefaultRep = reifyObject(new MT1Iso[T]()(eT))
    lazy val tag = {
      implicit val tagT = eT.tag
      weakTypeTag[MT1Iso[T]]
    }
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("T" -> (eT -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class MT1CompanionCtor extends CompanionDef[MT1CompanionCtor] {
    def selfType = MT1CompanionElem
    override def toString = "MT1Companion"
    @scalan.OverloadId("fromData")
    def apply[T](p: Rep[MT1Data[T]]): Rep[MT1[T]] = {
      implicit val eT = p._1.elem
      isoMT1[T].to(p)
    }

    @scalan.OverloadId("fromFields")
    def apply[T](data: Rep[T], size: Rep[Int]): Rep[MT1[T]] =
      mkMT1(data, size)

    def unapply[T](p: Rep[MetaTest[T]]) = unmkMT1(p)
  }
  lazy val MT1Rep: Rep[MT1CompanionCtor] = new MT1CompanionCtor
  lazy val MT1: MT1CompanionCtor = proxyMT1Companion(MT1Rep)
  implicit def proxyMT1Companion(p: Rep[MT1CompanionCtor]): MT1CompanionCtor = {
    proxyOps[MT1CompanionCtor](p)
  }

  implicit case object MT1CompanionElem extends CompanionElem[MT1CompanionCtor] {
    lazy val tag = weakTypeTag[MT1CompanionCtor]
    protected def getDefaultRep = MT1Rep
  }

  implicit def proxyMT1[T](p: Rep[MT1[T]]): MT1[T] =
    proxyOps[MT1[T]](p)

  implicit class ExtendedMT1[T](p: Rep[MT1[T]]) {
    def toData: Rep[MT1Data[T]] = {
      implicit val eT = p.data.elem
      isoMT1(eT).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoMT1[T](implicit eT: Elem[T]): Iso[MT1Data[T], MT1[T]] =
    reifyObject(new MT1Iso[T]()(eT))

  case class MT2Ctor[T, R]
      (override val indices: Rep[T], override val values: Rep[R], override val size: Rep[Int])
    extends MT2[T, R](indices, values, size) with Def[MT2[T, R]] {
    implicit val eT = indices.elem;
implicit val eR = values.elem
    lazy val selfType = element[MT2[T, R]]
  }
  // elem for concrete class
  class MT2Elem[T, R](val iso: Iso[MT2Data[T, R], MT2[T, R]])(implicit val eT: Elem[T], val eR: Elem[R])
    extends MetaTestElem[(T, R), MT2[T, R]]
    with ConcreteElem[MT2Data[T, R], MT2[T, R]] {
    override lazy val parent: Option[Elem[_]] = Some(metaTestElement(pairElement(element[T],element[R])))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("T" -> (eT -> scalan.util.Invariant), "R" -> (eR -> scalan.util.Invariant))

    override def convertMetaTest(x: Rep[MetaTest[(T, R)]]) = // Converter is not generated by meta
!!!("Cannot convert from MetaTest to MT2: missing fields List(indices, values)")
    override def getDefaultRep = MT2(element[T].defaultRepValue, element[R].defaultRepValue, 0)
    override lazy val tag = {
      implicit val tagT = eT.tag
      implicit val tagR = eR.tag
      weakTypeTag[MT2[T, R]]
    }
  }

  // state representation type
  type MT2Data[T, R] = (T, (R, Int))

  // 3) Iso for concrete class
  class MT2Iso[T, R](implicit eT: Elem[T], eR: Elem[R])
    extends EntityIso[MT2Data[T, R], MT2[T, R]] with Def[MT2Iso[T, R]] {
    override def from(p: Rep[MT2[T, R]]) =
      (p.indices, p.values, p.size)
    override def to(p: Rep[(T, (R, Int))]) = {
      val Pair(indices, Pair(values, size)) = p
      MT2(indices, values, size)
    }
    lazy val eFrom = pairElement(element[T], pairElement(element[R], element[Int]))
    lazy val eTo = new MT2Elem[T, R](self)
    lazy val selfType = new MT2IsoElem[T, R](eT, eR)
    def productArity = 2
    def productElement(n: Int) = n match {
      case 0 => eT
      case 1 => eR
    }
  }
  case class MT2IsoElem[T, R](eT: Elem[T], eR: Elem[R]) extends Elem[MT2Iso[T, R]] {
    def getDefaultRep = reifyObject(new MT2Iso[T, R]()(eT, eR))
    lazy val tag = {
      implicit val tagT = eT.tag
      implicit val tagR = eR.tag
      weakTypeTag[MT2Iso[T, R]]
    }
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("T" -> (eT -> scalan.util.Invariant), "R" -> (eR -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class MT2CompanionCtor extends CompanionDef[MT2CompanionCtor] {
    def selfType = MT2CompanionElem
    override def toString = "MT2Companion"
    @scalan.OverloadId("fromData")
    def apply[T, R](p: Rep[MT2Data[T, R]]): Rep[MT2[T, R]] = {
      implicit val eT = p._1.elem;
implicit val eR = p._2.elem
      isoMT2[T, R].to(p)
    }

    @scalan.OverloadId("fromFields")
    def apply[T, R](indices: Rep[T], values: Rep[R], size: Rep[Int]): Rep[MT2[T, R]] =
      mkMT2(indices, values, size)

    def unapply[T, R](p: Rep[MetaTest[(T, R)]]) = unmkMT2(p)
  }
  lazy val MT2Rep: Rep[MT2CompanionCtor] = new MT2CompanionCtor
  lazy val MT2: MT2CompanionCtor = proxyMT2Companion(MT2Rep)
  implicit def proxyMT2Companion(p: Rep[MT2CompanionCtor]): MT2CompanionCtor = {
    proxyOps[MT2CompanionCtor](p)
  }

  implicit case object MT2CompanionElem extends CompanionElem[MT2CompanionCtor] {
    lazy val tag = weakTypeTag[MT2CompanionCtor]
    protected def getDefaultRep = MT2Rep
  }

  implicit def proxyMT2[T, R](p: Rep[MT2[T, R]]): MT2[T, R] =
    proxyOps[MT2[T, R]](p)

  implicit class ExtendedMT2[T, R](p: Rep[MT2[T, R]]) {
    def toData: Rep[MT2Data[T, R]] = {
      implicit val eT = p.indices.elem;
implicit val eR = p.values.elem
      isoMT2(eT, eR).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoMT2[T, R](implicit eT: Elem[T], eR: Elem[R]): Iso[MT2Data[T, R], MT2[T, R]] =
    reifyObject(new MT2Iso[T, R]()(eT, eR))

  registerModule(MetaTestsModule)

  lazy val MetaTest: Rep[MetaTestCompanionCtor] = new MetaTestCompanionCtor {
  }

  object MT0Methods {
    object test {
      def unapply(d: Def[_]): Option[Rep[MT0]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT0Elem] && method.getName == "test" =>
          Some(receiver).asInstanceOf[Option[Rep[MT0]]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT0]] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object give {
      def unapply(d: Def[_]): Option[Rep[MT0]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT0Elem] && method.getName == "give" =>
          Some(receiver).asInstanceOf[Option[Rep[MT0]]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT0]] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object elem {
      def unapply(d: Def[_]): Option[Rep[MT0]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT0Elem] && method.getName == "elem" =>
          Some(receiver).asInstanceOf[Option[Rep[MT0]]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT0]] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object MT0CompanionMethods {
  }

  def mkMT0
    (size: Rep[Int]): Rep[MT0] = {
    new MT0Ctor(size)
  }
  def unmkMT0(p: Rep[MetaTest[Unit]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: MT0Elem @unchecked =>
      Some((p.asRep[MT0].size))
    case _ =>
      None
  }

  object MT1Methods {
    object test {
      def unapply(d: Def[_]): Option[Rep[MT1[T]] forSome {type T}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT1Elem[_]] && method.getName == "test" =>
          Some(receiver).asInstanceOf[Option[Rep[MT1[T]] forSome {type T}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT1[T]] forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object give {
      def unapply(d: Def[_]): Option[Rep[MT1[T]] forSome {type T}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT1Elem[_]] && method.getName == "give" =>
          Some(receiver).asInstanceOf[Option[Rep[MT1[T]] forSome {type T}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT1[T]] forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  def mkMT1[T]
    (data: Rep[T], size: Rep[Int]): Rep[MT1[T]] = {
    new MT1Ctor[T](data, size)
  }
  def unmkMT1[T](p: Rep[MetaTest[T]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: MT1Elem[T] @unchecked =>
      Some((p.asRep[MT1[T]].data, p.asRep[MT1[T]].size))
    case _ =>
      None
  }

  object MT2Methods {
    object test {
      def unapply(d: Def[_]): Option[Rep[MT2[T, R]] forSome {type T; type R}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT2Elem[_, _]] && method.getName == "test" =>
          Some(receiver).asInstanceOf[Option[Rep[MT2[T, R]] forSome {type T; type R}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT2[T, R]] forSome {type T; type R}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object give {
      def unapply(d: Def[_]): Option[Rep[MT2[T, R]] forSome {type T; type R}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MT2Elem[_, _]] && method.getName == "give" =>
          Some(receiver).asInstanceOf[Option[Rep[MT2[T, R]] forSome {type T; type R}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MT2[T, R]] forSome {type T; type R}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  def mkMT2[T, R]
    (indices: Rep[T], values: Rep[R], size: Rep[Int]): Rep[MT2[T, R]] = {
    new MT2Ctor[T, R](indices, values, size)
  }
  def unmkMT2[T, R](p: Rep[MetaTest[(T, R)]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: MT2Elem[T, R] @unchecked =>
      Some((p.asRep[MT2[T, R]].indices, p.asRep[MT2[T, R]].values, p.asRep[MT2[T, R]].size))
    case _ =>
      None
  }

  object MetaTestMethods {
    object test {
      def unapply(d: Def[_]): Option[Rep[MetaTest[T]] forSome {type T}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MetaTestElem[_, _]] && method.getName == "test" =>
          Some(receiver).asInstanceOf[Option[Rep[MetaTest[T]] forSome {type T}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MetaTest[T]] forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object give {
      def unapply(d: Def[_]): Option[Rep[MetaTest[T]] forSome {type T}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MetaTestElem[_, _]] && method.getName == "give" =>
          Some(receiver).asInstanceOf[Option[Rep[MetaTest[T]] forSome {type T}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MetaTest[T]] forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object size {
      def unapply(d: Def[_]): Option[Rep[MetaTest[T]] forSome {type T}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MetaTestElem[_, _]] && method.getName == "size" =>
          Some(receiver).asInstanceOf[Option[Rep[MetaTest[T]] forSome {type T}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[MetaTest[T]] forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object MetaTestCompanionMethods {
  }
}

object MetaTestsModule extends scalan.ModuleInfo {
  val dump = "H4sIAAAAAAAAALVXXYhbRRSe3M02u9m/bnQrVovbbcRt1WS3KBX2QbbbXa1kf9ibrri2lcnNJE57f8Z7JzGRUnwqxYIPpQ8i+FAQBFkU6YtYKWIRxIe+i29KQRCl9MFSweKZuT+5+bnJLtI8DHfmzjlnzvd958zN1p+o37HRpKNhHZsZg3CcUeXzvMPT6rJVrOjkGCn9al1LXb50/3MF7d5EI9TZoDavYJ2+R4qbaMI6u2hQvmrTsmuQtzHlOTSyaHLK62lDLnJ0KOeGyYow2U5h0p7FXA6l8nVG1LppmdQIPGR7ewibgZtHX7cxY8RuOcpsb0fNhuBqEJsacbhlOxztd+2zmqXrROPUMrPUMCocF3SSzVGHw/7dmmVqNuFEXdCx4xDnHXQexXNogAiXNJgPynl9lTX8tp9LQgrHEn7d/euEyTzrBkej3nFWmTgK7ElQg1k290MkwN3bVtGfxk0MCyiVO4OrOAshylmV29Qsg+WY1UyjMNmVQ0MMa2dxmayApVhKQB4O0UsCbrmlxmKIMQZiOizPkmlAkwmgyQho0iqxqdAOFi/XbKtWR+4v1odQTbh4rocL3wNZNIvpD05qb95ThwxFGNdkjoPg46kITUsyAMkf1y87d1+5ekRByU2UpM58weE21niYaA+vIWyaFpfHDSDEdhn4moriS0aZhz0AabxgFes+2ZplMGyCJw/YYWBKpxrlYrNYG/P46YgyUMkZ8bfGAfQg36gaFrbzjOn1m+dunPvtyZ/HFdQnRFhjdshtH7jtko6UwgLWdUhH4X5wiJp0mVItg4xP3aWnr17iCorlUKzWrK/Vwhlgcq5mo2HXwpXqA3rk319GS1zxiI9Mwo//beK773+//XJcQUozToOQgLoISfmH42hgGbzkgckAowNR7hlZsyn0MFolL968fuLOjZV+GSFVJCVc0fkG1ivErREvXiO2CKVMH+QofsJ062W4Jsa9Ykg25sku+QVyeeaPv4o/zKCTEkQpMh/rbekaXKRe+vibp8naFwoa2JR9YEnHZalwQfcx4mibaMCqEttdT1SxLp46qjzhpe+JJIy3K5jJSMEwIsiYqzFR1376Qy73K5ZJ0ktr6b/Vn65sCerF+8cAQAdKWsZ+nKM+aHBe0mLcx1FsBlaPmyGIfWDFONEFG797fnXhwsSdT996RNb8QIFyA7P0zA4q3i/Qh1jRKJCNm9f+xlwM06C24eX8zEI46nTrdgAK9rS8Go2F8Ex5oHlot9aKtxzLt4h4rk3UMmjIKh0QLsMDq0XMcQSrrSEiPHTWhRiebxODtGk/VSzW7FMhed9jfFEnRo+8h2sS0tmGd1GL+6L7CTD65T9HPzuw94kHCkq8hvpLUGROR2X1F6yKWfTVA18NnNT4UX8t3qweUAu2sRF8TFQxXIWgXo72+LVY4VTPbnjrbgXCb7IhrOngacOvzD1eJsI2c9x0vfL0s19vvUtvHVyS/TlE0U6k1KUsGclXmE5euH7/9MX3X2Wy6bU18o7MBNP1NqLEWNi5TBPULFLoAv9Lqbuq4qLo5mO9p4+HqnYxqD3TajdfjzZvZ0CWyuFwqYjxjS46vBg8neq598PG3nDQUKRZFK3OPrgGmhPYbutzx486KytYTYbCzW6jl4/70Ts09PA3zHbRbDr4qdbTX2mGEnYPNmUPXI94xQrXmuH1KHG1TkXUsOpdZgDr+XufrBy6de22/HJKimsR7nkz+BMS/mJqhnosOIP7xyMEsSgHiPAfk3rpVKgOAAA="
}
}


package scalan.pointers

import scala.reflect.runtime.universe._
import scalan.util.{Covariant, Invariant}
import scalan.{Base, ScalanExp}

trait PointerOps extends Base { self: ScalanExp =>
  class Pointer[A](implicit val eA: Elem[A])
  class Scalar[A](implicit val eA: Elem[A])

  def nullPtr[A: Elem]: Rep[Pointer[A]]
  def scalarPtr[A: Elem](source: Rep[A]): Rep[Pointer[A]]
  def arrayPtr[A: Elem](xs: Rep[Array[A]]): Rep[Pointer[A]]

  case class PointerElem[A, To <: Pointer[A]](eItem: Elem[A]) extends EntityElem[To] {
    def parent: Option[Elem[_]] = None
    override lazy val typeArgs = TypeArgs("A" -> (eItem -> Invariant))
    override lazy val tag = {
      implicit val ttag = eItem.tag
      weakTypeTag[Pointer[A]].asInstanceOf[WeakTypeTag[To]]
    }
    def convertPointer(x: Rep[Pointer[A]]): Rep[To] = x.asRep[To]
    protected def getDefaultRep = convertPointer(nullPtr[A](eItem))
  }
  implicit def PointerElement[A](implicit eItem: Elem[A]): Elem[Pointer[A]] =
    new PointerElem[A, Pointer[A]](eItem)
  
  case class ScalarElem[A: Elem, To <: Scalar[A]](eItem: Elem[A]) extends EntityElem[To] {
    def parent: Option[Elem[_]] = None
    override lazy val typeArgs = TypeArgs("A" -> (eItem -> Covariant))
    override lazy val tag = {
      implicit val ttag = eItem.tag
      weakTypeTag[Scalar[A]].asInstanceOf[WeakTypeTag[To]]
    }
    def convertScalar(x: Rep[Scalar[A]]): Rep[To] = x.asRep[To]
    protected def getDefaultRep = convertScalar(eItem.defaultRepValue.asRep[Scalar[A]])
  }
  implicit def ScalarElement[A](implicit eItem: Elem[A]): Elem[Scalar[A]] =
    new ScalarElem[A, Scalar[A]](eItem)
}

trait PointerOpsExp extends PointerOps { self: ScalanExp =>

  case class NullPtr[A]()(implicit val eA: Elem[A]) extends BaseDef[Pointer[A]]
  def nullPtr[A: Elem]: Exp[Pointer[A]] = NullPtr[A]()

  // type Scalar for case when Exp[A] is a value and no pointer can be applied
  case class CreateScalar[A](source: Exp[A])(implicit val eA: Elem[A]) extends BaseDef[Scalar[A]]

  case class ScalarPtr[A](xScalar: Exp[Scalar[A]])(implicit val eA: Elem[A]) extends BaseDef[Pointer[A]]
  def scalarPtr[A: Elem](source: Exp[A]): Exp[Pointer[A]] = {
    source.elem match {
      case be: BaseElem[_] => ScalarPtr(CreateScalar(source))
      case _ => !!!(s"not allowed to make scalar pointer for non-BaseElem: ${source.elem}", source)
    }
  }

  case class ArrayPtr[A](xs: Exp[Array[A]])(implicit val eA: Elem[A]) extends BaseDef[Pointer[A]]
  def arrayPtr[A: Elem](xs: Exp[Array[A]]): Exp[Pointer[A]] = ArrayPtr(xs)

}

package scalan

import java.lang.reflect.Method
import scala.reflect.runtime.universe._
import scalan.compilation.{GraphVizConfig, GraphVizExport}
import scalan.util.Invariant

trait TypeWrappers extends GraphVizExport with Base { scalan: Scalan =>
  trait TypeWrapperDef[TBase, TWrapper] extends Def[TWrapper] {
    def wrappedValue: Rep[TBase]
  }

  class BaseTypeElem[TBase, TWrapper <: TypeWrapperDef[TBase, TWrapper]]
  (val wrapperElem: Elem[TWrapper])(implicit tag: WeakTypeTag[TBase])
  // null can be used because getDefaultRep is overridden
    extends BaseElem[TBase](null.asInstanceOf[TBase]) { self =>
    override protected def getDefaultRep = {
      val wrapperDefaultValue = wrapperElem.defaultRepValue
      unwrapTypeWrapperRep(wrapperDefaultValue)
    }
    override def getName(f: TypeDesc => String) =
      s"${super.getName(f)}{base type, wrapper: ${f(wrapperElem)}}"
  }

  class BaseTypeElem1[A, CBase[_], TWrapper <: TypeWrapperDef[CBase[A], TWrapper]]
  (wrapperElem: Elem[TWrapper])(implicit val eItem: Elem[A], val cont: Cont[CBase])
    extends BaseTypeElem[CBase[A], TWrapper](wrapperElem)(cont.tag(eItem.tag)) {
    override def equals(other: Any) = other match {
      case other: BaseTypeElem1[_, _, _] =>
        this.eq(other) ||
          (other.canEqual(this) &&
            this.runtimeClass == other.runtimeClass &&
            cont == other.cont &&
            eItem == other.eItem)
      case _ => false
    }
    override def buildTypeArgs =
      TypeArgs("A" -> (eItem -> Invariant), "CBase" -> (cont -> Invariant), "TWrapper" -> (wrapperElem -> Invariant))
  }

  trait WrapperElem[TBase, TWrapper] extends EntityElem[TWrapper] {
    val baseElem: Elem[TBase]
    def eTo: Elem[_]
  }

  abstract class WrapperElem1[A, Abs, CBase[_], CW[_]](eA: Elem[A], contBase: Cont[CBase], contW: Cont[CW])
    extends EntityElem1[A, Abs, CW](eA, contW) with WrapperElem[CBase[A], Abs] {
    val baseElem: Elem[CBase[A]]
    def eTo: Elem[_]
  }

  trait ExCompanion0[TBase]
  trait ExCompanion1[TBase[_]]
  trait ExCompanion2[TBase[_,_]]
  trait ExCompanion3[TBase[_,_,_]]

  final val ContainerLength = "ContainerLength"
  final val ContainerApply = "ContainerApply"

  def isValueAccessor(m: Method) = m.getName == "wrappedValue"

  def isWrapperElem(el: Elem[_]) = el match {
    case el: WrapperElem1[_,_,_,_] => true
    case el: WrapperElem[_,_] => true
    case _ => false
  }

  protected def unwrapTypeWrapperRep[TBase, TWrapper](x: Rep[TypeWrapperDef[TBase, TWrapper]]): Rep[TBase] =
    x.asInstanceOf[Rep[TBase]]

  override protected def nodeColor(td: TypeDesc)(implicit config: GraphVizConfig) = td match {
    case _: BaseTypeElem[_, _] => "blue"
    case _ => super.nodeColor(td)
  }

  def unwrapSyms(syms: List[AnyRef]): List[AnyRef] = {
    syms.map {
      case obj if !obj.isInstanceOf[Rep[_]] => obj
      case HasViews(s, iso) => s
      case s => s
    }
  }

  def unwrapMethodCall[T](mc: MethodCall, unwrappedReceiver: Rep[_], eUnwrappedRes: Elem[T]): Rep[T] = {
    val eUnwrappedReceiver = unwrappedReceiver.elem
    val newArgs = unwrapSyms(mc.args)
    val argClasses = newArgs.map {
      case a: Rep[a] => a.elem.runtimeClass
      case a => a.getClass
    }

    // keep unwrapped method because real method may have different signature
    // moreover it is not necessary exists
    // this should be handled in Backend using config
    val newCall = mkMethodCall(unwrappedReceiver, mc.method, newArgs, true, eUnwrappedRes)
    newCall.asRep[T]
  }
  def unwrapNewObj[T](clazz: Class[T], args: List[AnyRef], neverInvoke: Boolean, eUnwrappedRes: Elem[T]): Rep[T] = {
    val newArgs = unwrapSyms(args)
    val newObj = new NewObject[T](eUnwrappedRes, newArgs, neverInvoke)
    newObj
  }

  override def rewriteDef[T](d: Def[T]) = d match {
    // Rule: W(a).m(args) ==> iso.to(a.m(unwrap(args)))
    case mc @ MethodCall(Def(wrapper: TypeWrapperDef[_, _]), m, args, neverInvoke) if !isValueAccessor(m) =>
      val resultElem = mc.selfType
      val wrapperIso = getIsoByElem(resultElem)
      wrapperIso match {
        case Def(iso: IsoUR[base, ext]) =>
          if (iso.isIdentity)
            super.rewriteDef(d)
          else {
            val eRes = iso.eFrom
            val newCall = unwrapMethodCall(mc, wrapper.wrappedValue, eRes)
            iso.to(newCall)
          }
      }

    case _ => super.rewriteDef(d)
  }
}
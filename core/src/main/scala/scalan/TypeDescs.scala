package scalan

import scala.annotation.implicitNotFound
import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._
import scala.reflect.{AnyValManifest, ClassTag}
import scalan.meta.ScalanAst._
import scalan.util._

trait TypeDescs extends Base { self: Scalan =>
  sealed trait TypeDesc extends Serializable {
    def getName(f: TypeDesc => String): String
    lazy val name: String = getName(_.name)

    // <> to delimit because: [] is used inside name; {} looks bad with structs.
    override def toString = s"${getClass.getSimpleName}<$name>"
  }

  object TypeDesc {
    def apply(tpe: STpeExpr, env: TypeArgSubst): TypeDesc = tpe match {
      case STpePrimitive(name,_) =>
        val methodName = name + "Element"
        callMethod(methodName, Array(), Nil)
      case STraitCall("$bar", List(a, b)) =>
        sumElement(TypeDesc(a, env).asElem, TypeDesc(b, env).asElem)
      case STpeTuple(List(a, b)) =>
        pairElement(TypeDesc(a, env).asElem, TypeDesc(b, env).asElem)
      case STpeFunc(a, b) =>
        funcElement(TypeDesc(a, env).asElem, TypeDesc(b, env).asElem)
      case STraitCall(name, Nil) =>
        env.get(name) match {
          case Some(t) => t
          case None =>
            val methodName = StringUtil.lowerCaseFirst(name + "Element")
            callMethod(methodName, Array(), List())
        }
      case STraitCall(name, args) =>
        val argDescs = args.map(p => TypeDesc(p, env))
        val argClasses = argDescs.map {
          case e: Elem[_] => classOf[Elem[_]]
          case c: Cont[_] => classOf[Cont[Any]]
          case d => !!!(s"Unknown type descriptior $d")
        }.toArray[Class[_]]
        val methodName = StringUtil.lowerCaseFirst(name + "Element")
        callMethod(methodName, argClasses, argDescs)

      case _ => !!!(s"Unexpected STpeExpr: $tpe")
    }
  }

  type TypeArgSubst = Map[String, TypeDesc]
  type TypePredicate = Elem[_] => Boolean
  def AllTypes(e: Elem[_]): Boolean = true
  val emptySubst = Map.empty[String, TypeDesc]

  private def callMethod(methodName: String, descClasses: Array[Class[_]], paramDescs: List[AnyRef]): TypeDesc = {
    try {
      val method = self.getClass.getMethod(methodName, descClasses: _*)
      try {
        val result = method.invoke(self, paramDescs: _*)
        result.asInstanceOf[Elem[_]]
      } catch {
        case e: Exception =>
          !!!(s"Failed to invoke $methodName with parameters $paramDescs", e)
      }
    } catch {
      case e: Exception =>
        !!!(s"Failed to find elem-creating method with name $methodName with parameters $paramDescs: ${e.getMessage}")
    }
  }

  implicit class STpeExprOpsForTypeDesc(tpe: STpeExpr) {
    def toTypeDesc(env: TypeArgSubst = Map()): TypeDesc = TypeDesc(tpe, env)
  }

  implicit class TypeDescOps(d: TypeDesc) {
    def asElem[B]: Elem[B] = d.asInstanceOf[Elem[B]]
    def asElemOption[B]: Option[Elem[B]] = if (isElem) Some(d.asInstanceOf[Elem[B]]) else None
    def asCont[C[_]]: Cont[C] = d.asInstanceOf[Cont[C]]
    def asContOption[C[_]]: Option[Cont[C]] = if (isCont) Some(d.asInstanceOf[Cont[C]]) else None
    def isElem: Boolean = d.isInstanceOf[Elem[_]]
    def isCont: Boolean = d.isInstanceOf[Cont[Any] @unchecked]
    def toTpeExpr: STpeExpr = d match {
      case e: Elem[_] => e.toTpeExpr
      case _ => ???
    }
    def applySubst(subst: TypeArgSubst): TypeDesc = d match {
      case e: ArgElem => subst.getOrElse(e.argName, e)
      //      case ae: ArrayElem[a] =>
      //        arrayElement(ae.eItem.applySubst(subst).asElem)
      //      case ae: ArrayBufferElem[a] =>
      //      case ae: ListElem[a] =>
      //      case ae: StructElem[a] =>
      //        val tpes = (ae.fieldNames zip ae.fieldElems).map { case (name, el) =>
      //          BaseType(name, List(Type(el)))
      //        }
      //        StructType(tpes.toList)
      //      case ee: EntityElem[a] =>
      //        val ent = Entity(entityDef(ee).name)
      //        val elemSubst = ee.typeArgs
      //        val subst = ent.typeArgs.map((a: ArgElem) => {
      //          val el = elemSubst.getOrElse(a.name, a)
      //          (a, el)
      //        })
      //        new EntityApply(ent, subst.toMap)
      //      case be: BaseTypeElem1[a,tExt,cBase] =>
      //        val a = Type(be.eItem)
      //        BaseType(be.runtimeClass.getSimpleName, List(a))
      //      case be: BaseTypeElem[tBase,tExt] =>
      //        BaseType(be.runtimeClass.getSimpleName, Nil)
      //      case _ if e == UnitElement => BaseType("Unit")
      //      case _ if e == BooleanElement => BaseType("Boolean")
      //      case _ if e == ByteElement => BaseType("Byte")
      //      case _ if e == ShortElement => BaseType("Short")
      //      case _ if e == IntElement => BaseType("Int")
      //      case _ if e == LongElement => BaseType("Long")
      //      case _ if e == FloatElement => BaseType("Float")
      //      case _ if e == DoubleElement => BaseType("Double")
      //      case _ if e == StringElement => BaseType("String")
      //      case _ if e == CharElement => BaseType("Char")
      //      case pe: PairElem[_,_] =>
      //        val a = Type(pe.eFst)
      //        val b = Type(pe.eSnd)
      //        Tuple(List(a,b))
      //      case pe: SumElem[_,_] =>
      //        val a = Type(pe.eLeft)
      //        val b = Type(pe.eRight)
      //        Sum(List(a,b))
      //      case pe: FuncElem[_,_] =>
      //        val a = Type(pe.eDom)
      //        val b = Type(pe.eRange)
      //        Func(a,b)
      case _ => d
    }
  }

  implicit class ElemOps(e: Elem[_]) {
    def toTpeExpr: STpeExpr = e match {
      case _ if e == UnitElement => TpeUnit
      case _ if e == BooleanElement => TpeBoolean
      case _ if e == ByteElement => TpeByte
      case _ if e == ShortElement => TpeShort
      case _ if e == IntElement => TpeInt
      case _ if e == LongElement => TpeLong
      case _ if e == FloatElement => TpeFloat
      case _ if e == DoubleElement => TpeDouble
      case _ if e == StringElement => TpeString
      case _ if e == CharElement => TpeChar
      case pe: PairElem[_,_] =>
        val a = pe.eFst.toTpeExpr
        val b = pe.eSnd.toTpeExpr
        STpeTuple(List(a,b))
      case pe: FuncElem[_,_] =>
        val a = pe.eDom.toTpeExpr
        val b = pe.eRange.toTpeExpr
        STpeFunc(a,b)
      case ee: EntityElem[a] =>
        STraitCall(ee.entityName, ee.typeArgs.map { case (_, (a, _)) => a.toTpeExpr }.toList)
  //      case ae: StructElem[a] =>
      //        val tpes = ae.fields.map { case (name, el) =>
      //          BaseType(name, List(el))
      //        }
      //        StructType(tpes.toList)


      //      case be: BaseTypeElem1[a,tExt,cBase] =>
      //        val a = Type(be.eItem)
      //        BaseType(be.runtimeClass.getSimpleName, List(a))
      //      case be: BaseTypeElem[tBase,tExt] =>
      //        BaseType(be.runtimeClass.getSimpleName, Nil)


      case _ => sys.error(s"Cannot perform toTpeExpr for $e")
    }
  }

  type LElem[A] = Lazy[Elem[A]] // lazy element

  /**
    * Reified type representation in Scalan.
    *
    * @tparam A The represented type
    */
  @implicitNotFound(msg = "No Elem available for ${A}.")
  abstract class Elem[A] extends TypeDesc { _: scala.Equals =>
    def tag: WeakTypeTag[A]
    final lazy val classTag: ClassTag[A] = ReflectionUtil.typeTagToClassTag(tag)
    // classTag.runtimeClass is cheap, no reason to make it lazy
    final def runtimeClass: Class[_] = classTag.runtimeClass

    def buildTypeArgs: ListMap[String, (TypeDesc, Variance)] = ListMap()
    lazy val typeArgs: ListMap[String, (TypeDesc, Variance)] = buildTypeArgs
    def typeArgsIterator = typeArgs.valuesIterator.map(_._1)
    final def copyWithTypeArgs(args: Iterator[TypeDesc]) = {
      try {
        val res = _copyWithTypeArgs(args)
        assert(!args.hasNext)
        res
      } catch {
        case e: NoSuchElementException =>
          !!!(s"Not enough elements in the iterator supplied to $this._copyWithTypeArgs", e)
      }
    }
    protected def _copyWithTypeArgs(args: Iterator[TypeDesc]): Elem[_] =
      if (typeArgs.isEmpty)
        this
      else
        cachedElemI(this.getClass, args)
    def mapTypeArgs(f: Elem[_] => Elem[_]) = {
      val newTypeArgs = typeArgsIterator.map {
        case e: Elem[_] =>
          f(e)
        case x => x
      }
      copyWithTypeArgs(newTypeArgs)
    }

    // should only be called by defaultRepValue
    protected def getDefaultRep: Rep[A]
    lazy val defaultRepValue = getDefaultRep

    override def getName(f: TypeDesc => String) = {
      import ClassTag._
      val className = classTag match {
        case _: AnyValManifest[_] | Any | AnyVal | AnyRef | Object | Nothing | Null => classTag.toString
        case objectTag => objectTag.runtimeClass.getSimpleName
      }
      if (typeArgs.isEmpty)
        className
      else {
        val typeArgString = typeArgsIterator.map(f).mkString(", ")
        s"$className[$typeArgString]"
      }
    }

    def leastUpperBound(e: Elem[_]): Elem[_] =
      commonBound(e, isUpper = true)

    def greatestLowerBound(e: Elem[_]): Elem[_] =
      commonBound(e, isUpper = false)

    /**
      * Helper for leastUpperBound and greatestLowerBound.
      * Should be protected, but then it can't be accessed in subclasses, see
      * http://stackoverflow.com/questions/4621853/protected-members-of-other-instances-in-scala
      */
    def commonBound(e: Elem[_], isUpper: Boolean): Elem[_] = {
      if (this eq e)
        this
      else
        _commonBound(e, isUpper).getOrElse {
          if (isUpper) AnyElement else NothingElement
        }
    }

    // overridden in BaseElem, EntityElem, StructElem
    protected def _commonBound(other: Elem[_], isUpper: Boolean): Option[Elem[_]] = {
      if (this.getClass == other.getClass) {
        // empty type args case is covered too, since copyWithTypeArgs will return `this`
        val newArgs = this.typeArgsIterator.zip(other.typeArgs.valuesIterator).map {
          case (arg1: Elem[_], (arg2: Elem[_], variance)) if variance != Invariant =>
            val isUpper1 = variance match {
              case Covariant => isUpper
              case Contravariant => !isUpper
              case Invariant => !!!("variance can't be Invariant if we got here")
            }
            arg1.commonBound(arg2, isUpper1)
          case (arg1, (arg2, _)) =>
            if (arg1 == arg2)
              arg1
            else {
              // return from the entire _commonBound method! This really throws an exception, but
              // it works since the iterator will be consumed by copyWithTypeArgs below and the
              // exception will get thrown there
              return None
            }
        }
        val newElem = copyWithTypeArgs(newArgs)
        Some(newElem)
      } else
        None
    }

    def <:<(e: Elem[_]) = tag.tpe <:< e.tag.tpe
    def >:>(e: Elem[_]) = e <:< this

    if (isDebug) {
      debug$ElementCounter(this) += 1
    }
  }
  object Elem {
    def unapply[T, E <: Elem[T]](s: Rep[T]): Option[E] = Some(s.elem.asInstanceOf[E])

    def pairify(es: Iterator[Elem[_]]): Elem[_] = {
      def step(a: Elem[_], b: Elem[_], tail: Iterator[Elem[_]]): Elem[_] = {
        if (tail.hasNext) {
          val c = tail.next()
          pairElement(a, step(b, c, tail))
        }
        else {
          pairElement(a, b)
        }
      }
      val a = es.next()
      val b = es.next()
      step(a, b, es)
    }
  }

  private val debug$ElementCounter = counter[Elem[_]]

  private[scalan] def getConstructor(clazz: Class[_]) = {
    val constructors = clazz.getDeclaredConstructors()
    if (constructors.length != 1)
      !!!(s"Element class $clazz has ${constructors.length} constructors, 1 expected")
    else
      constructors(0)
  }

  // FIXME See https://github.com/scalan/scalan/issues/252
  def cachedElem[E <: Elem[_]](args: AnyRef*)(implicit tag: ClassTag[E]) = {
    cachedElem0(tag.runtimeClass, None, args).asInstanceOf[E]
  }

  private def cachedElemI(clazz: Class[_], argsIterator: Iterator[TypeDesc]) = {
    val constructor = getConstructor(clazz)
    // -1 because the constructor includes `self` argument, see cachedElem0 below
    val args = argsIterator.take(constructor.getParameterTypes.length - 1).toSeq
    cachedElem0(clazz, Some(constructor), args)
  }

  protected val elemCache = collection.mutable.Map.empty[(Class[_], Seq[AnyRef]), AnyRef]

  private def cachedElem0(clazz: Class[_], optConstructor: Option[java.lang.reflect.Constructor[_]], args: Seq[AnyRef]) = {
    elemCache.getOrElseUpdate(
      (clazz, args), {
        val constructor = optConstructor.getOrElse(getConstructor(clazz))
        val constructorArgs = self +: args
        constructor.newInstance(constructorArgs: _*).asInstanceOf[AnyRef]
      }).asInstanceOf[Elem[_]]
  }

  def cleanUpTypeName(tpe: Type) = tpe.toString.
    replaceAll("[A-Za-z0-9_.]*this.", "").
    replace("scala.math.Numeric$", "").
    replace("scala.math.Ordering$", "").
    replace("scala.", "").
    replace("java.lang.", "").
    replaceAll("""[^# \[\],>]*[#$]""", "")

  def element[A](implicit ea: Elem[A]): Elem[A] = ea

  class BaseElem[A](defaultValue: A)(implicit val tag: WeakTypeTag[A]) extends Elem[A] with Serializable with scala.Equals {
    protected def getDefaultRep = toRep(defaultValue)(this)
    override def canEqual(other: Any) = other.isInstanceOf[BaseElem[_]]
    override def equals(other: Any) = other match {
      case other: BaseElem[_] =>
        this.eq(other) ||
          (other.canEqual(this) &&
            this.runtimeClass == other.runtimeClass &&
            noTypeArgs)
      case _ => false
    }

    override def buildTypeArgs = {
      assert(noTypeArgs)
      TypeArgs()
    }
    override protected def _commonBound(other: Elem[_], isUpper: Boolean): Option[Elem[_]] =
      if (this == other) Some(this) else None

    private[this] lazy val noTypeArgs =
      if (tag.tpe.typeArgs.isEmpty)
        true
      else
        !!!(s"${getClass.getSimpleName} is a BaseElem for a generic type, must override equals and typeArgs.")
    override def hashCode = tag.tpe.hashCode
  }

  case class PairElem[A, B](eFst: Elem[A], eSnd: Elem[B]) extends Elem[(A, B)] {
    assert(eFst != null && eSnd != null)
    lazy val tag = {
      implicit val tA = eFst.tag
      implicit val tB = eSnd.tag
      weakTypeTag[(A, B)]
    }
    override def getName(f: TypeDesc => String) = s"(${f(eFst)}, ${f(eSnd)})"
    override def buildTypeArgs = ListMap("A" -> (eFst -> Covariant), "B" -> (eSnd -> Covariant))
    protected def getDefaultRep = Pair(eFst.defaultRepValue, eSnd.defaultRepValue)
  }

  case class SumElem[A, B](eLeft: Elem[A], eRight: Elem[B]) extends Elem[A | B] {
    lazy val tag = {
      implicit val tA = eLeft.tag
      implicit val tB = eRight.tag
      weakTypeTag[A | B]
    }
    override def getName(f: TypeDesc => String) = s"(${f(eLeft)} | ${f(eRight)})"
    override def buildTypeArgs = ListMap("A" -> (eLeft -> Covariant), "B" -> (eRight -> Covariant))
    protected def getDefaultRep = mkLeft[A, B](eLeft.defaultRepValue)(eRight)
  }

  case class FuncElem[A, B](eDom: Elem[A], eRange: Elem[B]) extends Elem[A => B] {
    lazy val tag = {
      implicit val tA = eDom.tag
      implicit val tB = eRange.tag
      weakTypeTag[A => B]
    }
    override def getName(f: TypeDesc => String) = s"${f(eDom)} => ${f(eRange)}"
    override def buildTypeArgs = ListMap("A" -> (eDom -> Contravariant), "B" -> (eRange -> Covariant))
    protected def getDefaultRep = {
      val defaultB = eRange.defaultRepValue
      fun[A, B](_ => defaultB)(Lazy(eDom))
    }
  }

  class ArgElem(val tyArg: STpeArg) extends Elem[Any] with Serializable with scala.Equals {
    protected def getDefaultRep = toRep(null.asInstanceOf[Any])(this)
    val tag = ReflectionUtil.createArgTypeTag(tyArg.name).asInstanceOf[WeakTypeTag[Any]]
    def argName = tyArg.name
    override def buildTypeArgs = {
      assert(noTypeArgs)
      TypeArgs()
    }
    override protected def _copyWithTypeArgs(args: Iterator[TypeDesc]): Elem[_] = {
      assert(noTypeArgs)
      this
    }

    private[this] lazy val noTypeArgs =
      if (tag.tpe.typeArgs.isEmpty)
        true
      else
        !!!(s"${getClass.getSimpleName} is a ArgElem for a generic type, must override equals and typeArgs.")

    override def getName(f: TypeDesc => String) = {
      if (typeArgs.isEmpty)
        tyArg.name
      else {
        val typeArgString = typeArgsIterator.map(f).mkString(", ")
        s"${tyArg.name}[$typeArgString]"
      }
    }

    def variance = tyArg.variance
    def isCovariant = tyArg.isCovariant
    def tyExpr = tyArg.toTraitCall
    def toDesc(env: Map[ArgElem,TypeDesc]): TypeDesc = env.get(this) match {
      case Some(d) => d
      case None =>
        !!!(s"Don't know how to convert ArgElem $this to TypeDesc")
    }

    override def <:<(e: Elem[_]) = if (this == e) true else super.<:<(e)

    override def canEqual(other: Any) = other.isInstanceOf[ArgElem]
    override def equals(other: Any) = other match {
      case other: ArgElem =>
        this.eq(other) ||
          (other.canEqual(this) && this.tyArg == other.tyArg)
      case _ => false
    }
    override def hashCode = tag.tpe.hashCode
  }
  object ArgElem {
    def apply(name: String): ArgElem = new ArgElem(STpeArg(name, None, Nil))
    def apply(a: STpeArg) = new ArgElem(a)
    def unapply(t: ArgElem): Option[STpeArg] = Some(t.tyArg)
  }

  val AnyElement: Elem[Any] = new BaseElem[Any](null)
  val AnyRefElement: Elem[AnyRef] = new BaseElem[AnyRef](null)
  // very ugly casts but should be safe
  val NothingElement: Elem[Nothing] =
    new BaseElem[Null](null)(weakTypeTag[Nothing].asInstanceOf[WeakTypeTag[Null]]).asElem[Nothing]
  implicit val BooleanElement: Elem[Boolean] = new BaseElem(false)
  implicit val ByteElement: Elem[Byte] = new BaseElem(0.toByte)
  implicit val ShortElement: Elem[Short] = new BaseElem(0.toShort)
  implicit val IntElement: Elem[Int] = new BaseElem(0)
  implicit val LongElement: Elem[Long] = new BaseElem(0L)
  implicit val FloatElement: Elem[Float] = new BaseElem(0.0F)
  implicit val DoubleElement: Elem[Double] = new BaseElem(0.0)
  implicit val UnitElement: Elem[Unit] = new BaseElem(())
  implicit val StringElement: Elem[String] = new BaseElem("")
  implicit val CharElement: Elem[Char] = new BaseElem('\u0000')

  implicit def pairElement[A, B](implicit ea: Elem[A], eb: Elem[B]): Elem[(A, B)] =
    cachedElem[PairElem[A, B]](ea, eb)
  implicit def sumElement[A, B](implicit ea: Elem[A], eb: Elem[B]): Elem[A | B] =
    cachedElem[SumElem[A, B]](ea, eb)
  implicit def funcElement[A, B](implicit ea: Elem[A], eb: Elem[B]): Elem[A => B] =
    cachedElem[FuncElem[A, B]](ea, eb)
  ///implicit def elemElement[A](implicit ea: Elem[A]): Elem[Elem[A]]

  implicit def PairElemExtensions[A, B](eAB: Elem[(A, B)]): PairElem[A, B] = eAB.asInstanceOf[PairElem[A, B]]
  implicit def SumElemExtensions[A, B](eAB: Elem[A | B]): SumElem[A, B] = eAB.asInstanceOf[SumElem[A, B]]
  implicit def FuncElemExtensions[A, B](eAB: Elem[A => B]): FuncElem[A, B] = eAB.asInstanceOf[FuncElem[A, B]]
  //  implicit def ElemElemExtensions[A](eeA: Elem[Elem[A]]): ElemElem[A] = eeA.asInstanceOf[ElemElem[A]]

  implicit def toLazyElem[A](implicit eA: Elem[A]): LElem[A] = Lazy(eA)

  def TypeArgs(descs: (String, (TypeDesc, Variance))*) = ListMap(descs: _*)

  object TagImplicits {
    implicit def elemToClassTag[A](implicit elem: Elem[A]): ClassTag[A] = elem.classTag
  }

  /** Returns the argument by default, can be overridden for specific types */
  def concretizeElem(e: Elem[_]): Elem[_] = e.mapTypeArgs(concretizeElem)

  // can be removed and replaced with assert(value.elem == elem) after #72
  def assertElem(value: Rep[_], elem: Elem[_]): Unit = assertElem(value, elem, "")
  def assertElem(value: Rep[_], elem: Elem[_], hint: => String): Unit = {
    assert(value.elem == elem,
      s"${value.toStringWithType} doesn't have type ${elem.name}" + (if (hint.isEmpty) "" else s"; $hint"))
  }
  def assertEqualElems[A](e1: Elem[A], e2: Elem[A], m: => String): Unit =
    assert(e1 == e2, s"Element $e1 != $e2: $m")

  def withElemOf[A, R](x: Rep[A])(block: Elem[A] => R): R = block(x.elem)
  def withResultElem[A, B, R](f: Rep[A => B])(block: Elem[B] => R): R = block(withElemOf(f) { e => e.eRange })

  @implicitNotFound(msg = "No Cont available for ${F}.")
  trait Cont[F[_]] extends TypeDesc {
    def tag[T](implicit tT: WeakTypeTag[T]): WeakTypeTag[F[T]]
    def lift[T](implicit eT: Elem[T]): Elem[F[T]]
    def unlift[T](implicit eFT: Elem[F[T]]): Elem[T]
    def getElem[T](fa: Rep[F[T]]): Elem[F[T]]
    def getItemElem[T](fa: Rep[F[T]]): Elem[T] = unlift(getElem(fa))
    def unapply[T](e: Elem[_]): Option[Elem[F[T]]]

    def getName(f: TypeDesc => String): String = {
      // note: will use WeakTypeTag[x], so x type parameter ends up in the result
      // instead of the actual type parameter it's called with (see below)
      def tpeA[x] = tag[x].tpe

      val tpe = tpeA[Nothing]

      val str = cleanUpTypeName(tpe)

      if (str.endsWith("[x]"))
        str.stripSuffix("[x]")
      else
        "[x]" + str
    }
    def isFunctor = this.isInstanceOf[Functor[F]]
  }

  def container[F[_]: Cont] = implicitly[Cont[F]]

  implicit def containerElem[F[_]:Cont, A:Elem]: Elem[F[A]] = container[F].lift(element[A])

  trait Functor[F[_]] extends Cont[F] {
    def map[A,B](a: Rep[F[A]])(f: Rep[A] => Rep[B]): Rep[F[B]]
  }
}

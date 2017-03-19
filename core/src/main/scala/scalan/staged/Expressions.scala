
package scalan.staged

import java.lang.reflect.Constructor

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.{TraversableOnce, mutable}
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions
import scala.reflect.runtime.universe._
import scalan.util.{ParamMirror, ReflectionUtil}
import scalan.{Base, Scalan, ScalanExp}
import scalan.common.Lazy
import scalan.compilation.GraphVizConfig

trait BaseExp extends Base { scalan: ScalanExp =>
  type Rep[+A] = Exp[A]

  /**
   * constants/symbols (atomic)
   */
  abstract class Exp[+T] extends Staged[T] {
    def elem: Elem[T @uncheckedVariance]
    
    private[scalan] var isRec = false
    def isRecursive: Boolean = isRec
    private[scalan] def isRecursive_=(b: Boolean) = { isRec = b }

    def isVar: Boolean = this match {
      case Def(_) => false
      case _ => true
    }

    def isConst: Boolean = this match {
      case Def(Const(_)) => true
      case _ => isCompanion
    }

    def isCompanion: Boolean = elem.isInstanceOf[CompanionElem[_]]

    def toStringWithDefinition: String

    def show(): Unit = show(defaultGraphVizConfig)
    def show(emitMetadata: Boolean): Unit = show(defaultGraphVizConfig.copy(emitMetadata = emitMetadata))
    def show(config: GraphVizConfig): Unit = showGraphs(this)(config)
  }
  type ExpAny = Exp[_]

  abstract class BaseDef[+T](implicit val selfType: Elem[T @uncheckedVariance]) extends Def[T]

  case class Const[T](x: T)(implicit val eT: Elem[T]) extends BaseDef[T]

  abstract class Transformer {
    def apply[A](x: Rep[A]): Rep[A]
    def isDefinedAt(x: Rep[_]): Boolean
    def domain: Set[Rep[_]]
    def apply[A](xs: Seq[Rep[A]]): Seq[Rep[A]] = xs map (e => apply(e))
    def apply[X,A](f: X=>Rep[A]): X=>Rep[A] = (z:X) => apply(f(z))
    def apply[X,Y,A](f: (X,Y)=>Rep[A]): (X,Y)=>Rep[A] = (z1:X,z2:Y) => apply(f(z1,z2))
    def onlySyms[A](xs: List[Rep[A]]): List[Rep[A]] = xs map (e => apply(e)) collect { case e: Exp[A] => e }
  }
  def IdTransformer = MapTransformer.Empty

  trait TransformerOps[Ctx <: Transformer] {
    def empty: Ctx
    def add[A](ctx: Ctx, kv: (Rep[A], Rep[A])): Ctx
    def merge(ctx1: Ctx, ctx2: Ctx): Ctx = ctx2.domain.foldLeft(ctx1) {
      case (t, s: Rep[a]) => add(t, (s, ctx2(s)))
    }
  }

  implicit class TransformerEx[Ctx <: Transformer](self: Ctx)(implicit ops: TransformerOps[Ctx]) {
    def +[A](kv: (Rep[A], Rep[A])) = ops.add(self, kv)
    def ++(kvs: Map[Rep[A], Rep[A]] forSome {type A}) = kvs.foldLeft(self)((ctx, kv) => ops.add(ctx, kv))
    def merge(other: Ctx): Ctx = ops.merge(self, other)
  }

  override protected def stagingExceptionMessage(message: String, syms: Seq[Exp[_]]) = {
    // Skip syms already in the message, assume that's the only source for s<N>
    val symsNotInMessage = syms.map(_.toString).filterNot(message.contains)

    if (symsNotInMessage.isEmpty) {
      message
    } else {
      val between = if (message.isEmpty)
        ""
      else
        message.last match {
          // determine whether the message lacks ending punctuation
          case '.' | ';' | '!' | '?' => " "
          case _ => ". "
        }

      message + between + s"Sym${if (symsNotInMessage.length > 1) "s" else ""}: ${symsNotInMessage.mkString(", ")}"
    }
  }

  private[this] case class ReflectedProductClass(constructor: Constructor[_], paramMirrors: List[ParamMirror], hasScalanParameter: Boolean)

  private[this] val baseType = typeOf[Base]

  private[this] def reflectProductClass(clazz: Class[_], d: Product) = {
    val constructors = clazz.getDeclaredConstructors
    assert(constructors.length == 1, s"Every class extending Def must have one constructor, $clazz has ${constructors.length}")
    val constructor = constructors(0)

    val paramMirrors = ReflectionUtil.paramMirrors(d)

    val hasScalanParam = constructor.getParameterTypes.headOption match {
      case None => false
      case Some(firstParamClazz) =>
        // note: classOf[Base].isAssignableFrom(firstParamClazz) can give wrong result due to the way
        // Scala compiles traits inheriting from classes
        val firstParamTpe = ReflectionUtil.classToSymbol(firstParamClazz).toType
        firstParamTpe <:< baseType
    }

    ReflectedProductClass(constructor, paramMirrors, hasScalanParam)
  }

  private[this] val defClasses = collection.mutable.Map.empty[Class[_], ReflectedProductClass]

  def transformDef[A](d: Def[A], t: Transformer): Exp[A] = d match {
    case c: Const[_] => c.self
    case comp: CompanionDef[_] => comp.self
    case _ =>
      val newD = transformProduct(d, t).asInstanceOf[Def[A]]
      reifyObject(newD)
  }

  protected def transformProductParam(x: Any, t: Transformer): Any = x match {
    case e: Exp[_] => t(e)
    case seq: Seq[_] => seq.map(transformProductParam(_, t))
    case arr: Array[_] => arr.map(transformProductParam(_, t))
    case opt: Option[_] => opt.map(transformProductParam(_, t))
    case p: Product if p.productArity != 0 => transformProduct(p, t)
    case x => x
  }

  def transformProduct(p: Product, t: Transformer): Product = {
    val clazz = p.getClass
    val ReflectedProductClass(constructor, paramMirrors, hasScalanParameter) =
      defClasses.getOrElseUpdate(clazz, reflectProductClass(clazz, p))

    val pParams = paramMirrors.map(_.bind(p).get)
    val transformedParams = pParams.map(transformProductParam(_, t))
    val finalParams =
      (if (hasScalanParameter)
        scalan :: transformedParams
      else
        transformedParams).asInstanceOf[List[AnyRef]]

    try {
      val transformedP = constructor.newInstance(finalParams: _*).asInstanceOf[Product]
      transformedP
    } catch {
      case e: Exception =>
        !!!(
          s"""
             |Failed to invoke constructor $clazz(${constructor.getParameterTypes.map(_.getSimpleName).mkString(", ")}) with parameters ${finalParams.mkString(", ")}
             |
             |Graph nodes have scalan cake as the first parameter ($$owner).
             |Check that the trait where class $clazz is defined extends Base.
             |""".stripMargin, e)
    }
  }

  def fresh[T](implicit leT: LElem[T]): Exp[T]
  def findDefinition[T](s: Exp[T]): Option[TableEntry[T]]
  def findDefinition[T](thunk: Exp[_], d: Def[T]): Option[TableEntry[T]]
  def createDefinition[T](s: Exp[T], d: Def[T]): TableEntry[T]

  /**
   * Updates the universe of symbols and definitions, then rewrites until fix-point
   * @param d A new graph node to add to the universe
   * @param newSym A symbol that will be used if d doesn't exist in the universe
   * @tparam T Type of the result
   * @return The symbol of the graph which is semantically(up to rewrites) equivalent to d
   */
  protected[scalan] def toExp[T](d: Def[T], newSym: => Exp[T]): Exp[T]

  implicit def reifyObject[A](obj: Def[A]): Rep[A] = {
    toExp(obj, fresh[A](Lazy(obj.selfType)))
  }

  override def toRep[A](x: A)(implicit eA: Elem[A]):Rep[A] = eA match {
    case _: BaseElem[_] => Const(x)
    case _: FuncElem[_, _] => Const(x)
    case pe: PairElem[a, b] =>
      val x1 = x.asInstanceOf[(a, b)]
      implicit val eA = pe.eFst
      implicit val eB = pe.eSnd
      Pair(toRep(x1._1), toRep(x1._2))
    case se: SumElem[a, b] =>
      val x1 = x.asInstanceOf[a | b]
      implicit val eA = se.eLeft
      implicit val eB = se.eRight
      x1.fold(l => SLeft[a, b](l), r => SRight[a, b](r))
    case _ =>
      x match {
        // this may be called instead of reifyObject implicit in some cases
        case d: BaseExp#Def[A @unchecked] => reifyObject(d.asInstanceOf[Def[A]])
        case _ => super.toRep(x)(eA)
      }
  }

  def valueFromRep[A](x: Rep[A]): A = x match {
    case Def(Const(x)) => x
    case _ => delayInvoke
  }

  def def_unapply[T](e: Exp[T]): Option[Def[T]] = findDefinition(e).map(_.rhs)

//  override def repDef_getElem[T <: Def[_]](x: Rep[T]): Elem[T] = x.elem
//  override def rep_getElem[T](x: Rep[T]): Elem[T] = x.elem

  object Var {
    def unapply[T](e: Exp[T]): Option[Exp[T]] = e match {
      case Def(_) => None
      case _ => Some(e)
    }
  }

  object ExpWithElem {
    def unapply[T](s: Exp[T]): Option[(Exp[T],Elem[T])] = Some((s, s.elem))
  }

  /**
   * Used for staged methods which can't be implemented based on other methods.
   * This just returns a value of the desired type.
   */
  def defaultImpl[T](implicit elem: Elem[T]): Exp[T] = elem.defaultRepValue

  abstract class Stm // statement (links syms and definitions)

  implicit class StmOps(stm: Stm) {
    def lhs: List[Exp[Any]] = stm match {
      case TableEntry(sym, rhs) => sym :: Nil
    }

    def defines[A](sym: Exp[A]): Option[Def[A]] = stm match {
      case TableEntry(`sym`, rhs: Def[A] @unchecked) => Some(rhs)
      case _ => None
    }

    def defines[A](rhs: Def[A]): Option[Exp[A]] = stm match {
      case TableEntry(sym: Exp[A] @unchecked, `rhs`) => Some(sym)
      case _ => None
    }
  }
  
  trait TableEntry[+T] extends Stm {
    def sym: Exp[T]
    def lambda: Option[Exp[_]]
    def rhs: Def[T]
    def isLambda = rhs.isInstanceOf[Lambda[_, _]]
  }

  trait TableEntryCompanion {
    def apply[T](sym: Exp[T], rhs: Def[T]): TableEntry[T]
    def apply[T](sym: Exp[T], rhs: Def[T], lam: Exp[_]): TableEntry[T]
    def unapply[T](tp: TableEntry[T]): Option[(Exp[T], Def[T])]
  }

  val TableEntry: TableEntryCompanion = null
  protected val globalThunkSym: Exp[_]

  object DefTableEntry {
    def unapply[T](e: Exp[T]): Option[TableEntry[T]] = findDefinition(e)
  }

  def decompose[T](d: Def[T]): Option[Exp[T]] = None

  def flatMapWithBuffer[A, T](iter: Iterator[A], f: A => TraversableOnce[T]): List[T] = {
    // performance hotspot: this is the same as
    // iter.toList.flatMap(f(_)) but faster
    val out = new ListBuffer[T]
    while (iter.hasNext) {
      val e = iter.next()
      out ++= f(e)
    }
    out.result()
  }

  def flatMapIterable[A, T](iterable: Iterable[A], f: A => TraversableOnce[T]) =
    flatMapWithBuffer(iterable.iterator, f)

  def flatMapProduct[T](p: Product, f: Any => TraversableOnce[T]): List[T] = {
    val iter = p.productIterator
    flatMapWithBuffer(iter, f)
  }

  // regular data (and effect) dependencies
  def syms(e: Any): List[Exp[_]] = e match {
    case s: Exp[_] => List(s)
    case s: Iterable[_] =>
      flatMapWithBuffer(s.iterator, syms)
    // All case classes extend Product!
    case p: Product =>
      flatMapProduct(p, syms)
    case _ => Nil
  }
  def dep(e: Exp[_]): List[Exp[_]] = e match {
    case Def(d) => syms(d)
    case _ => Nil
  }
  def dep(d: Def[_]): List[Exp[_]] = syms(d)

  // symbols which are bound in a definition
  def boundSyms(e: Any): List[Exp[Any]] = e match {
    case ss: Iterable[Any] => flatMapWithBuffer(ss.iterator, boundSyms)
    case p: Product => flatMapProduct(p, boundSyms)
    case _ => Nil
  }

  // symbols which are bound in a definition, but also defined elsewhere
  def tunnelSyms(e: Any): List[Exp[Any]] = e match {
    case ss: Iterable[Any] => flatMapWithBuffer(ss.iterator, tunnelSyms)
    case p: Product => flatMapProduct(p, tunnelSyms)
    case _ => Nil
  }

  // symbols of effectful components of a definition
  def effectSyms(x: Any): List[Exp[Any]] = x match {
    case ss: Iterable[Any] => flatMapWithBuffer(ss.iterator, effectSyms)
    case p: Product => flatMapProduct(p, effectSyms)
    case _ => Nil
  }

  // soft dependencies: they are not required but if they occur, 
  // they must be scheduled before
  def softSyms(e: Any): List[Exp[Any]] = e match {
    // empty by default
    //case s: Exp[Any] => List(s)
    case ss: Iterable[Any] => flatMapWithBuffer(ss.iterator, softSyms)
    case p: Product => flatMapProduct(p, softSyms)
    case _ => Nil
  }

  // generic symbol traversal: f is expected to call rsyms again
  def rsyms[T](e: Any)(f: Any=>List[T]): List[T] = e match {
    case s: Exp[Any] => f(s)
    case ss: Iterable[Any] => flatMapWithBuffer(ss.iterator, f)
    case p: Product => flatMapProduct(p, f)
    case _ => Nil
  }

  // frequency information for dependencies: used/computed
  // often (hot) or not often (cold). used to drive code motion.
  def symsFreq(e: Any): List[(Exp[Any], Double)] = e match {
    case s: Exp[Any] => List((s,1.0))
    case ss: Iterable[Any] => flatMapWithBuffer(ss.iterator, symsFreq)
    case p: Product => flatMapProduct(p, symsFreq)
    //case _ => rsyms(e)(symsFreq)
    case _ => Nil
  }

  def freqNormal(e: Any) = symsFreq(e)
  def freqHot(e: Any) = symsFreq(e).map(p=>(p._1,p._2*1000.0))
  def freqCold(e: Any) = symsFreq(e).map(p=>(p._1,p._2*0.5))

  implicit class ExpForSomeOps(symbol: Exp[_]) {
    def inputs: List[Exp[Any]] = dep(symbol)
    def getDeps: List[Exp[_]] = symbol match {
      case Def(g: AstGraph) => g.freeVars.toList
      case _ => this.inputs
    }

    /** Shallow dependencies don't look into branches of IfThenElse  */
    def getShallowDeps: List[ExpAny] = symbol match {
      case Def(IfThenElse(c, _, _)) => List(c)
      case _ => getDeps
    }

    def isLambda: Boolean = symbol match {
      case Def(_: Lambda[_, _]) => true
      case _ => false
    }
    def tp: TableEntry[_] = findDefinition(symbol).getOrElse {
      !!!(s"No definition found for $symbol", symbol)
    }
    def sameScopeAs(other: Exp[_]): Boolean = this.tp.lambda == other.tp.lambda
  }

  implicit class DefForSomeOps(d: Def[_]) {
    def getDeps: List[Exp[_]] = d match {
      case g: AstGraph => g.freeVars.toList
      case _ => syms(d)
    }

    def asDef[T] = d.asInstanceOf[Def[T]]
  }

  case class HasArg(predicate: Exp[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Def[T]] = {
      val args = dep(d)
      if (args.exists(predicate)) Some(d) else None
    }
  }

  case class FindArg(predicate: Exp[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Exp[_]] = {
      val args = dep(d)
      for { a <- args.find(predicate) } yield a
    }
  }

  def rewrite[T](s: Exp[T]): Exp[_] = s match {
    case Def(d) => rewriteDef(d)
    case _ => rewriteVar(s)
  }

  def rewriteDef[T](d: Def[T]): Exp[_] = null

  def rewriteVar[T](s: Exp[T]): Exp[_] = null
}

/**
 * The Expressions trait houses common AST nodes. It also manages a list of encountered Definitions which
 * allows for common sub-expression elimination (CSE).
 *
 * @since 0.1
 */
trait Expressions extends BaseExp { scalan: ScalanExp =>
  /**
   * A Sym is a symbolic reference used internally to refer to expressions.
   */
  object Sym {
    private var currId = 0
    def fresh[T: LElem]: Exp[T] = {
      currId += 1
      Sym(currId)
    }
  }
  case class Sym[+T](id: Int)(implicit private val eT: LElem[T @uncheckedVariance]) extends Exp[T] {
    override def elem: Elem[T @uncheckedVariance] = this match {
      case Def(d) => d.selfType
      case _ => eT.value
    }
    def varName = "s" + id
    override def toString = varName

    lazy val definition = findDefinition(this).map(_.rhs)
    def toStringWithDefinition = toStringWithType + definition.map(d => s" = $d").getOrElse("")
  }

  def fresh[T: LElem]: Exp[T] = Sym.fresh[T]

  case class TableEntrySingle[T](sym: Exp[T], rhs: Def[T], lambda: Option[Exp[_]]) extends TableEntry[T]

  override val TableEntry: TableEntryCompanion = new TableEntryCompanion {
    def apply[T](sym: Exp[T], rhs: Def[T]) = new TableEntrySingle(sym, rhs, None)
    def apply[T](sym: Exp[T], rhs: Def[T], lam: Exp[_]) = new TableEntrySingle(sym, rhs, Some(lam))
    def unapply[T](tp: TableEntry[T]): Option[(Exp[T], Def[T])] = Some((tp.sym, tp.rhs))
    //def unapply[T](s: Exp[T]): Option[TableEntry[T]] = findDefinition(s)
  }
  protected val globalThunkSym: Exp[_] = fresh[Int] // we could use any type here
  private[this] val expToGlobalDefs: mutable.Map[Exp[_], TableEntry[_]] = mutable.HashMap.empty
  private[this] val defToGlobalDefs: mutable.Map[(Exp[_], Def[_]), TableEntry[_]] = mutable.HashMap.empty

  def findDefinition[T](s: Exp[T]): Option[TableEntry[T]] =
    expToGlobalDefs.get(s).asInstanceOf[Option[TableEntry[T]]]

  def findDefinition[T](thunk: Exp[_], d: Def[T]): Option[TableEntry[T]] =
    defToGlobalDefs.get((thunk,d)).asInstanceOf[Option[TableEntry[T]]]

  def findOrCreateDefinition[T](d: Def[T], newSym: => Exp[T]): Exp[T] = {
    val optScope = thunkStack.top
    val optFound = optScope match {
      case Some(scope) =>
        scope.findDef(d)
      case None =>
        findDefinition(globalThunkSym, d)
    }
    val te = optFound.getOrElse {
      createDefinition(optScope, newSym, d)
    }
    assert(te.rhs == d, s"${if (optFound.isDefined) "Found" else "Created"} unequal definition ${te.rhs} with symbol ${te.sym.toStringWithType} for $d")
    te.sym

  }

  def createDefinition[T](s: Exp[T], d: Def[T]): TableEntry[T] =
    createDefinition(thunkStack.top, s, d)

  private def createDefinition[T](optScope: Option[ThunkScope], s: Exp[T], d: Def[T]): TableEntry[T] = {
    val te = lambdaStack.top match {
      case Some(fSym) => TableEntry(s, d, fSym)
      case _ => TableEntry(s, d)
    }
    optScope match {
      case Some(scope) =>
        defToGlobalDefs += (scope.thunkSym, te.rhs) -> te
        scope += te
      case None =>
        defToGlobalDefs += (globalThunkSym, te.rhs) -> te
    }

    expToGlobalDefs += te.sym -> te
    te
  }

  /**
   * Updates the universe of symbols and definitions, then rewrites until fix-point
   * @param d A new graph node to add to the universe
   * @param newSym A symbol that will be used if d doesn't exist in the universe
   * @tparam T
   * @return The symbol of the graph which is semantically(up to rewrites) equivalent to d
   */
  protected[scalan] def toExp[T](d: Def[T], newSym: => Exp[T]): Exp[T] = {
    var res = findOrCreateDefinition(d, newSym)
    var currSym = res
    var currDef = d
    do {
      currSym = res
      val ns = rewrite(currSym).asRep[T]
      ns match {
        case null =>
          currDef = null
        case Def(someOtherD) =>
          res = ns
          currDef = someOtherD
        case _ =>
          res = ns
          currDef = null
      }
    } while (res != currSym && currDef != null)
    res
  }
}


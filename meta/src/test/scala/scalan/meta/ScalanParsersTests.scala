package scalan.meta

import scalan.BaseNestedTests
import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.Global
import scalan.meta.ScalanAst._

class ScalanParsersTests extends BaseMetaTests with Examples {

  import compiler._
  import scalan.meta.ScalanAst.{STraitCall => TC, SUnitDef => EMD, SClassDef => CD, STpeTuple => T, SMethodArg => MA, STraitDef => TD, SMethodDef => MD, SMethodArgs => MAs, SImportStat => IS}
  import scala.{List => L}
  val cols = parseModule(colsVirtModule)
  val un = cols.unitName

  describe("STpeExpr") {
    implicit val ctx = new ParseCtx(true)
    testSTpe(un, "Int", TpeInt)
    testSTpe(un, "(Int,Boolean)", STpeTuple(L(TpeInt, TpeBoolean)))
    testSTpe(un, "Int=>Boolean", STpeFunc(TpeInt, TpeBoolean))
    testSTpe(un, "Int=>Boolean=>Float", STpeFunc(TpeInt, STpeFunc(TpeBoolean, TpeFloat)))
    testSTpe(un, "(Int,Boolean=>Float)", STpeTuple(L(TpeInt, STpeFunc(TpeBoolean, TpeFloat))))
    testSTpe(un, "(Int,(Boolean=>Float))", STpeTuple(L(TpeInt, STpeFunc(TpeBoolean, TpeFloat))))
    testSTpe(un, "(Int,Boolean)=>Float", STpeFunc(STpeTuple(L(TpeInt, TpeBoolean)), TpeFloat))
    testSTpe(un, "Edge", TC("Edge", Nil))
    testSTpe(un, "Edge[V,E]", TC("Edge", L(TC("V", Nil), TC("E", Nil))))
    testSTpe(un, "Rep[A=>B]", TC("Rep", L(STpeFunc(TC("A", Nil), TC("B", Nil)))))
  }

  describe("SMethodDef") {
    implicit val ctx = new ParseCtx(true)
    testSMethod(un, "def f: Int", MD("f", Nil, Nil, Some(TpeInt), false, false, None, Nil, None))
    testSMethod(un, "@OverloadId(\"a\") implicit def f: Int", MD("f", Nil, Nil, Some(TpeInt), true, false, Some("a"), L(SMethodAnnotation("OverloadId",List(SConst("a")))), None))
    testSMethod(un,
      "def f(x: Int): Int",
      MD("f", Nil, L(MAs(List(MA(false, false, "x", TpeInt, None)))), Some(TpeInt), false, false, None, Nil, None))
    testSMethod(un,
      "def f[A <: T](x: A): Int",
      MD("f", L(STpeArg("A", Some(TC("T", Nil)), Nil)), L(MAs(L(MA(false, false, "x", TC("A", Nil), None)))), Some(TpeInt), false, false, None, Nil, None))
    testSMethod(un,
      "def f[A : Numeric]: Int",
      MD("f", L(STpeArg("A", None, L("Numeric"))), Nil, Some(TpeInt), false, false, None, Nil, None))
    testSMethod(un,
      "def f[A <: Int : Numeric : Fractional](x: A)(implicit y: A): Int",
      MD(
        "f",
        L(STpeArg("A", Some(TpeInt), L("Numeric", "Fractional"))),
        L(MAs(L(MA(false, false, "x", TC("A", Nil), None))), MAs(L(MA(true, false, "y", TC("A", Nil), None)))),
        Some(TpeInt), false, false, None, Nil, None))
  }

  describe("TraitDef") {
    implicit val ctx = new ParseCtx(true)
    val traitA = TD(un, "A", Nil, Nil, Nil, None, None)
    val traitEdgeVE = TD(un, "Edge", L(STpeArg("V", None, Nil), STpeArg("E", None, Nil)), Nil, Nil, None, None)

    testTrait("trait A", traitA)
    testTrait("trait A extends B",
      traitA.copy(ancestors = L(TC("B", Nil).toTypeApply)))
    testTrait("trait A extends B with C",
      traitA.copy(ancestors = L(TC("B", Nil).toTypeApply, TC("C", Nil).toTypeApply)))
    testTrait("trait Edge[V,E]", traitEdgeVE)
    testTrait("trait Edge[V,E]{}", traitEdgeVE)
    testTrait("trait Edge[V,E]{ def f[A <: T](x: A, y: (A,T)): Int }",
      traitEdgeVE.copy(
        body = L(MD("f", L(STpeArg("A", Some(TC("T", Nil)), Nil)),
          L(MAs(L(MA(false, false, "x", TC("A", Nil), None), MA(false, false, "y", T(L(TC("A", Nil), TC("T", Nil))), None)))),
          Some(TpeInt), false, false, None, Nil, None))))
    testTrait(
      """trait A {
        |  import scalan._
        |  type Rep[A] = A
        |  def f: (Int,A)
        |  @OverloadId("b")
        |  def g(x: Boolean): A
        |}""".stripMargin,
      TD(un, "A", Nil, Nil, L(
        IS("scalan._"),
        STpeDef("Rep", L(STpeArg("A", None, Nil)), TC("A", Nil)),
        MD("f", Nil, Nil, Some(T(L(TpeInt, TC("A", Nil)))), false, false, None, Nil, None),
        MD("g", Nil, L(MAs(L(MA(false, false, "x", TpeBoolean, None)))), Some(TC("A", Nil)), false, false, Some("b"), L(SMethodAnnotation("OverloadId",List(SConst("b")))), None)), None, None))

  }

  val reactiveTrait =
    """trait Reactive extends Scalan {
      |  type Obs[A] = Rep[Observable[A]]
      |  trait Observable[A] {
      |    implicit def eA: Elem[A]
      |  }
      |  class ObservableImpl[A](implicit val eA: Elem[A]) extends Observable[A] {
      |  }
      |}
    """.stripMargin

  describe("SClassDef") {
    implicit val ctx = new ParseCtx(true)
    val classA =
      CD(un, "A", Nil, SClassArgs(Nil), SClassArgs(Nil), Nil, Nil, None, None, false)
    val classEdgeVE =
      CD(un, "Edge", L(STpeArg("V", None, Nil), STpeArg("E", None, Nil)), SClassArgs(Nil), SClassArgs(Nil), Nil, Nil, None, None, false)
    testSClass("class A", classA)
    testSClass("class A extends B",
      classA.copy(ancestors = L(TC("B", Nil).toTypeApply)))
    testSClass("class A extends B with C",
      classA.copy(ancestors = L(TC("B", Nil).toTypeApply, TC("C", Nil).toTypeApply)))
    testSClass("class Edge[V,E]", classEdgeVE)
    testSClass("class Edge[V,E](val x: V){ def f[A <: T](x: A, y: (A,T)): Int }",
      classEdgeVE.copy(
        args = SClassArgs(L(SClassArg(false, false, true, "x", TC("V", Nil), None))),
        body = L(MD("f", L(STpeArg("A", Some(TC("T", Nil)), Nil)),
          L(MAs(L(MA(false, false, "x", TC("A", Nil), None), MA(false, false, "y", T(L(TC("A", Nil), TC("T", Nil))), None)))),
          Some(TpeInt), false, false, None, Nil, None))))
  }

  describe("SModuleDef") {
    implicit val ctx = new ParseCtx(true)
    val un = SName("scalan.rx", reactiveModule.moduleName)
    val tpeArgA = L(STpeArg("A", None, Nil))
    val ancObsA = L(TC("Observable", L(TC("A", Nil))))
    val argEA = L(SClassArg(true, false, true, "eA", TC("Elem", L(TC("A", Nil))), None, Nil, true))
    val entity = TD(un, "Observable", tpeArgA, Nil, L(SMethodDef("eA",List(),List(),Some(TC("Elem",L(TC("A",Nil)))),true,false, None, Nil, None, true)), None, None)
    val obsImpl1 = CD(un, "ObservableImpl1", tpeArgA, SClassArgs(Nil), SClassArgs(argEA), ancObsA.map(_.toTypeApply), Nil, None, None, false)
    val obsImpl2 = obsImpl1.copy(name = "ObservableImpl2")

    testModule(reactiveModule,
      EMD("scalan.rx", L(SImportStat("scalan._")), reactiveModule.moduleName,
        List(STpeDef("Obs", L(STpeArg("A",None,Nil)) , TC("Rep", ancObsA))),
        List(entity),
        L(obsImpl1, obsImpl2),
        Nil,
        None,
//        stdDslImpls = Some(SDeclaredImplementations(Map())),
//        expDslImpls = Some(SDeclaredImplementations(Map())),
        ancestors = L(STraitCall("Scalan", Nil).toTypeApply), None, true))
  }
}

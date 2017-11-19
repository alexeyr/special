package scalan.meta

import scalan.meta.ScalanAst._
import scalan.meta.ScalanAstTransformers.{RepTypeRemover, TypeTransformerInAst}

class TransformerTests extends ScalanAstTests with Examples {
  val cols = parseModule(colsVirtModule)
  context.addModule(cols)
  val trans = new TypeTransformerInAst(new RepTypeRemover())

  describe("Rep removing") {
    def test(m: SUnitDef, typeIn: SUnitDef => STpeExpr): Unit = {
      val before = typeIn(m)
      val newCols = trans.moduleTransform(m)
      val after = typeIn(newCols)
      after should be(context.RepTypeOf.unapply(before).get)
    }
    it("from method result type") {
      test(cols, m => getMethod(m, "Collection", "length").tpeRes.get)
      test(cols, m => getMethod(m, "Collection", "apply").tpeRes.get)
    }
    it("from method arg") {
      test(cols, m => getMethod(m, "Collection", "apply").allArgs(0).tpe)
    }
    it("from class arg") {
      test(cols, m => m.getEntity("ColOverArray").args.args(0).tpe)
    }
    it("from class val") {
      test(cols, m => getVal(m, "ColOverArray", "list").tpe.get)
    }
  }

}

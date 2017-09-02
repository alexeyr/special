package scalan.graphs

import scala.language.reflectiveCalls
import scalan._

class FrontTests extends BaseCtxTests {

  trait FrontProg extends ScalanExp with GraphsDsl {
    lazy val example = fun { (in: Rep[(Int, Front)]) =>
      val Pair(v, f) = in;
      f.append(v)
    }

    lazy val t1 = fun { (in: Rep[(Int,(Array[Int],Array[Boolean]))]) =>
      val Pair(v, Pair(xs, bits)) = in
      val c = Collection.fromArray(xs)
      val f = example(Pair(v, CollectionFront(c, BitSet(bits))))
      f.set.arr
    }
    lazy val t2 = fun { (in: Rep[(Int,(List[Int],Array[Boolean]))]) =>
      val Pair(v, Pair(xs, bits)) = in
      val c = Collection.fromList(xs)
      val f = example(Pair(v, CollectionFront(c, BitSet(bits))))
      f.set.arr
    }

    lazy val t3 = fun { (in: Rep[List[Int]]) => CollectionOverList(in).asRep[CollectionOverArray[Int]].arr }
  }

  class FrontProgStaged extends TestContext with FrontProg with GraphsDslExp {
  }
  class FrontProgStd extends ScalanDslStd with FrontProg with GraphsDslStd {
  }

  test("deepSpec") {
    val ctx = new FrontProgStaged
    ctx.emit("t1", ctx.t1)
    ctx.emit("t2", ctx.t2)
  }

  test("autoSuper") {
    val ctx = new FrontProgStaged
    ctx.emit("t3", ctx.t3)
  }



}

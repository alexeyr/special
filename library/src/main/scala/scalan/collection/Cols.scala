package scalan.collection {
  import scalan._

  trait Cols extends Base { self: Library =>
    trait Col[A] extends Def[Col[A]] {
      implicit def eA: Elem[A];
      def builder: Rep[ColBuilder];
      def arr: Rep[WArray[A]];
      def length: Rep[Int];
      def apply(i: Rep[Int]): Rep[A];
      def map[B](f: Rep[scala.Function1[A, B]]): Rep[Col[B]];
      def zip[B](ys: Rep[Col[B]]): Rep[PairCol[A, B]] = Col.this.builder.apply[A, B](this, ys)
    };
    trait PairCol[L, R] extends Col[scala.Tuple2[L, R]] {
      def ls: Rep[Col[L]];
      def rs: Rep[Col[R]]
    };
    trait ColBuilder extends Def[ColBuilder] {
      def apply[A, B](as: Rep[Col[A]], bs: Rep[Col[B]]): Rep[PairCol[A, B]];
      def fromArray[T](arr: Rep[WArray[T]]): Rep[Col[T]];
      def ddmvm(v: Rep[WArray[Double]]): Rep[Int] = {
        val xs: Rep[WArray[Int]] = WArray.fill[Int](v.length, Thunk(toRep(0.asInstanceOf[Int])));
        val c: Rep[WArray[scala.Tuple2[Int, Double]]] = xs.zip(v).map(fun(((d: Rep[scala.Tuple2[Int, Double]]) => d)));
        c.length
      }
    };
    trait ColCompanion
  }
}
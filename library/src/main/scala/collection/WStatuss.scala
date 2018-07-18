package collection {
  import scalan._

  import impl._

  import scala.wrappers.WrappersModule

  trait WStatuss extends Base { self: WrappersModule =>
    @External("Status") trait WStatus extends Def[WStatus];
    trait WStatusCompanion {
      @External def `Err ` : Rep[WValue];
      @External def Value(i: Rep[Int]): Rep[WValue];
      @External def `OK ` : Rep[WValue];
      @External def Value: Rep[WValue]
    }
  }
}
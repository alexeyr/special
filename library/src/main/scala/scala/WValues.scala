package scala {
  import scalan._

  import impl._

  import scala.wrappers.WrappersModule

  trait WValues extends Base { self: WrappersModule =>
    @External("Value") trait WValue extends Def[WValue];
    trait WValueCompanion
  }
}
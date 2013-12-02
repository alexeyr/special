/**
 * User: s00747473
 * Date: 11/16/13
 */
package tests.makro

import org.junit.Test
import org.junit.Assert._
import org.hamcrest.core.Is._


class EntityProviderTests extends {
  import makro.BoilerplateTool._

  val graphsPackage = "graphs"

  def testTraitAbs(entityTemplate: String, expected: String) =  {
    val d = parseEntityModule(entityTemplate)
    val p = new EntityFileGenerator(d)
    val code = p.getTraitAbs
    assertEquals(expected, code)
  }

  def testTraitSeq(entityTemplate: String, expected: String) =  {
    val d = parseEntityModule(entityTemplate)
    val p = new EntityFileGenerator(d)
    val code = p.getTraitSeq
    assertEquals(expected, code)
  }

  def testTraitExp(entityTemplate: String, expected: String) =  {
    val d = parseEntityModule(entityTemplate)
    val p = new EntityFileGenerator(d)
    val code = p.getTraitExp
    assertEquals(expected, code)
  }

  val testEntity: String =
    """package scalan.rx
     |import scalan._
     |import scalan.common.Common
     |trait Reactive extends ScalanDsl {
     |  type Obs[A] = Rep[Observable[A]]
     |  trait Observable[A] {
     |    implicit def eA: Elem[A]
     |    def value: Rep[A]
     |  }
     |  class ObservableImpl[A](val value: Rep[A])(implicit val eA: Elem[A]) extends Observable[A]
     |}
    """.stripMargin

  @Test def testAbs() {
    testTraitAbs(
      testEntity,
      """
        |trait ReactiveAbs extends Reactive {
        |
        |  // single proxy for each type family
        |  implicit def proxyObservable[A:Elem](p: Obs[A]): Observable[A] = {
        |    implicit val mA = element[A].manifest;
        |    proxyOps[Observable[A], Observable[A]](p)
        |  }
        |
        |}
        |""".stripMargin)
  }

  @Test def testSeq() {
    testTraitSeq(
      testEntity,
      """
        |trait ReactiveSeq extends Reactive { self: ScalanSeq =>
        |
        |  implicit def isoObservableImpl[A:Elem]:Iso[ObservableImplData[A], ObservableImpl[A]]
        |    = new ObservableImpl.Iso[A] with SeqIso[ObservableImplData[A], ObservableImpl[A]] { i =>
        |        // should use i as iso reference
        |        override lazy val eB = new SeqViewElem[ObservableImplData[A], ObservableImpl[A]]
        |                                    with ObservableImplElem[A] { val iso = i }
        |      }
        |
        |
        |  def mkObservableImpl[A:Elem](value: Rep[A])
        |    = new ObservableImpl[A](value)
        |  def unmkObservableImpl[A:Elem](p: Rep[ObservableImpl[A]])
        |    = Some((p.value))
        |
        |}
        |""".stripMargin)
  }

  @Test def testExp() {
    testTraitExp(
      testEntity,
      """
        |trait ReactiveExp extends Reactive with ProxyExp with ViewsExp { self: ScalanStaged =>
        |
        |  case class ExpObservableImpl[A]
        |      (override val value: Rep[A])
        |      (implicit override val eA: Elem[A])
        |    extends ObservableImpl[A](value) with UserType[ObservableImpl[A]] {
        |    def elem = element[ObservableImpl[A]]
        |    override def mirror(t: Transformer): Rep[_] = ExpObservableImpl[A](t(value))
        |  }
        |  addUserType(manifest[ExpObservableImpl[Any]])
        |
        |
        |  def mkObservableImpl[A:Elem](value: Rep[A])
        |    = new ExpObservableImpl[A](value)
        |  def unmkObservableImpl[A:Elem](p: Rep[ObservableImpl[A]])
        |    = Some((p.value))
        |
        |
        |  implicit def isoObservableImpl[A:Elem]:Iso[ObservableImplData[A], ObservableImpl[A]]
        |    = new ObservableImpl.Iso[A] with StagedIso[ObservableImplData[A], ObservableImpl[A]] { i =>
        |        // should use i as iso reference
        |        override lazy val eB = new StagedViewElem[ObservableImplData[A], ObservableImpl[A]]
        |                                    with ObservableImplElem[A] { val iso = i }
        |      }
        |
        |}
        |""".stripMargin)
  }

}
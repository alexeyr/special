package scalan.graphs

import scalan._
import scalan.collections.{CollectionsDslExp, CollectionsDslStd, CollectionsDsl}
import scalan.{ScalanStd, ScalanExp}
import scalan.common.OverloadHack.Overloaded1
import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeTag}
import scalan.meta.ScalanAst._

package impl {
// Abs -----------------------------------
trait GraphsAbs extends scalan.ScalanDsl with Graphs {
  self: GraphsDsl =>

  // single proxy for each type family
  implicit def proxyGraph[V, E](p: Rep[Graph[V, E]]): Graph[V, E] = {
    proxyOps[Graph[V, E]](p)(scala.reflect.classTag[Graph[V, E]])
  }

  // familyElem
  class GraphElem[V, E, To <: Graph[V, E]](implicit _eV: Elem[V], _eE: Elem[E])
    extends EntityElem[To] {
    def eV = _eV
    def eE = _eE
    lazy val parent: Option[Elem[_]] = None
    lazy val typeArgs = TypeArgs("V" -> (eV -> scalan.util.Invariant), "E" -> (eE -> scalan.util.Invariant))
    override lazy val tag = {
      implicit val tagV = eV.tag
      implicit val tagE = eE.tag
      weakTypeTag[Graph[V, E]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      implicit val eTo: Elem[To] = this
      val conv = fun {x: Rep[Graph[V, E]] => convertGraph(x) }
      tryConvert(element[Graph[V, E]], this, x, conv)
    }

    def convertGraph(x: Rep[Graph[V, E]]): Rep[To] = {
      x.selfType1 match {
        case _: GraphElem[_, _, _] => x.asRep[To]
        case e => !!!(s"Expected $x to have GraphElem[_, _, _], but got $e", x)
      }
    }

    override def getDefaultRep: Rep[To] = ???
  }

  implicit def graphElement[V, E](implicit eV: Elem[V], eE: Elem[E]): Elem[Graph[V, E]] =
    cachedElem[GraphElem[V, E, Graph[V, E]]](eV, eE)

  implicit case object GraphCompanionElem extends CompanionElem[GraphCompanionAbs] {
    lazy val tag = weakTypeTag[GraphCompanionAbs]
    protected def getDefaultRep = Graph
  }

  abstract class GraphCompanionAbs extends CompanionDef[GraphCompanionAbs] with GraphCompanion {
    def selfType = GraphCompanionElem
    override def toString = "Graph"
  }
  def Graph: Rep[GraphCompanionAbs]
  implicit def proxyGraphCompanionAbs(p: Rep[GraphCompanionAbs]): GraphCompanionAbs =
    proxyOps[GraphCompanionAbs](p)

  abstract class AbsAdjacencyGraph[V, E]
      (vertexValues: Coll[V], edgeValues: NColl[E], links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E])
    extends AdjacencyGraph[V, E](vertexValues, edgeValues, links) with Def[AdjacencyGraph[V, E]] {
    lazy val selfType = element[AdjacencyGraph[V, E]]
  }
  // elem for concrete class
  class AdjacencyGraphElem[V, E](val iso: Iso[AdjacencyGraphData[V, E], AdjacencyGraph[V, E]])(implicit override val eV: Elem[V], override val eE: Elem[E])
    extends GraphElem[V, E, AdjacencyGraph[V, E]]
    with ConcreteElem[AdjacencyGraphData[V, E], AdjacencyGraph[V, E]] {
    override lazy val parent: Option[Elem[_]] = Some(graphElement(element[V], element[E]))
    override lazy val typeArgs = TypeArgs("V" -> (eV -> scalan.util.Invariant), "E" -> (eE -> scalan.util.Invariant))

    override def convertGraph(x: Rep[Graph[V, E]]) = AdjacencyGraph(x.vertexValues, x.edgeValues, x.links)
    override def getDefaultRep = AdjacencyGraph(element[Collection[V]].defaultRepValue, element[NestedCollection[E]].defaultRepValue, element[NestedCollection[Int]].defaultRepValue)
    override lazy val tag = {
      implicit val tagV = eV.tag
      implicit val tagE = eE.tag
      weakTypeTag[AdjacencyGraph[V, E]]
    }
  }

  // state representation type
  type AdjacencyGraphData[V, E] = (Collection[V], (NestedCollection[E], NestedCollection[Int]))

  // 3) Iso for concrete class
  class AdjacencyGraphIso[V, E](implicit eV: Elem[V], eE: Elem[E])
    extends EntityIso[AdjacencyGraphData[V, E], AdjacencyGraph[V, E]] with Def[AdjacencyGraphIso[V, E]] {
    override def from(p: Rep[AdjacencyGraph[V, E]]) =
      (p.vertexValues, p.edgeValues, p.links)
    override def to(p: Rep[(Collection[V], (NestedCollection[E], NestedCollection[Int]))]) = {
      val Pair(vertexValues, Pair(edgeValues, links)) = p
      AdjacencyGraph(vertexValues, edgeValues, links)
    }
    lazy val eFrom = pairElement(element[Collection[V]], pairElement(element[NestedCollection[E]], element[NestedCollection[Int]]))
    lazy val eTo = new AdjacencyGraphElem[V, E](self)
    lazy val selfType = new AdjacencyGraphIsoElem[V, E](eV, eE)
    def productArity = 2
    def productElement(n: Int) = n match {
      case 0 => eV
      case 1 => eE
    }
  }
  case class AdjacencyGraphIsoElem[V, E](eV: Elem[V], eE: Elem[E]) extends Elem[AdjacencyGraphIso[V, E]] {
    def getDefaultRep = reifyObject(new AdjacencyGraphIso[V, E]()(eV, eE))
    lazy val tag = {
      implicit val tagV = eV.tag
      implicit val tagE = eE.tag
      weakTypeTag[AdjacencyGraphIso[V, E]]
    }
    lazy val typeArgs = TypeArgs("V" -> (eV -> scalan.util.Invariant), "E" -> (eE -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class AdjacencyGraphCompanionAbs extends CompanionDef[AdjacencyGraphCompanionAbs] with AdjacencyGraphCompanion {
    def selfType = AdjacencyGraphCompanionElem
    override def toString = "AdjacencyGraph"
    @scalan.OverloadId("fromData")
    def apply[V, E](p: Rep[AdjacencyGraphData[V, E]])(implicit eV: Elem[V], eE: Elem[E]): Rep[AdjacencyGraph[V, E]] =
      isoAdjacencyGraph(eV, eE).to(p)
    @scalan.OverloadId("fromFields")
    def apply[V, E](vertexValues: Coll[V], edgeValues: NColl[E], links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[AdjacencyGraph[V, E]] =
      mkAdjacencyGraph(vertexValues, edgeValues, links)

    def unapply[V, E](p: Rep[Graph[V, E]]) = unmkAdjacencyGraph(p)
  }
  lazy val AdjacencyGraphRep: Rep[AdjacencyGraphCompanionAbs] = new AdjacencyGraphCompanionAbs
  lazy val AdjacencyGraph: AdjacencyGraphCompanionAbs = proxyAdjacencyGraphCompanion(AdjacencyGraphRep)
  implicit def proxyAdjacencyGraphCompanion(p: Rep[AdjacencyGraphCompanionAbs]): AdjacencyGraphCompanionAbs = {
    proxyOps[AdjacencyGraphCompanionAbs](p)
  }

  implicit case object AdjacencyGraphCompanionElem extends CompanionElem[AdjacencyGraphCompanionAbs] {
    lazy val tag = weakTypeTag[AdjacencyGraphCompanionAbs]
    protected def getDefaultRep = AdjacencyGraph
  }

  implicit def proxyAdjacencyGraph[V, E](p: Rep[AdjacencyGraph[V, E]]): AdjacencyGraph[V, E] =
    proxyOps[AdjacencyGraph[V, E]](p)

  implicit class ExtendedAdjacencyGraph[V, E](p: Rep[AdjacencyGraph[V, E]])(implicit eV: Elem[V], eE: Elem[E]) {
    def toData: Rep[AdjacencyGraphData[V, E]] = isoAdjacencyGraph(eV, eE).from(p)
  }

  // 5) implicit resolution of Iso
  implicit def isoAdjacencyGraph[V, E](implicit eV: Elem[V], eE: Elem[E]): Iso[AdjacencyGraphData[V, E], AdjacencyGraph[V, E]] =
    reifyObject(new AdjacencyGraphIso[V, E]()(eV, eE))

  // 6) smart constructor and deconstructor
  def mkAdjacencyGraph[V, E](vertexValues: Coll[V], edgeValues: NColl[E], links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[AdjacencyGraph[V, E]]
  def unmkAdjacencyGraph[V, E](p: Rep[Graph[V, E]]): Option[(Rep[Collection[V]], Rep[NestedCollection[E]], Rep[NestedCollection[Int]])]

  abstract class AbsIncidenceGraph[V, E]
      (vertexValues: Coll[V], incMatrixWithVals: Coll[E], vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E])
    extends IncidenceGraph[V, E](vertexValues, incMatrixWithVals, vertexNum) with Def[IncidenceGraph[V, E]] {
    lazy val selfType = element[IncidenceGraph[V, E]]
  }
  // elem for concrete class
  class IncidenceGraphElem[V, E](val iso: Iso[IncidenceGraphData[V, E], IncidenceGraph[V, E]])(implicit override val eV: Elem[V], override val eE: Elem[E])
    extends GraphElem[V, E, IncidenceGraph[V, E]]
    with ConcreteElem[IncidenceGraphData[V, E], IncidenceGraph[V, E]] {
    override lazy val parent: Option[Elem[_]] = Some(graphElement(element[V], element[E]))
    override lazy val typeArgs = TypeArgs("V" -> (eV -> scalan.util.Invariant), "E" -> (eE -> scalan.util.Invariant))

    override def convertGraph(x: Rep[Graph[V, E]]) = IncidenceGraph(x.vertexValues, x.incMatrixWithVals, x.vertexNum)
    override def getDefaultRep = IncidenceGraph(element[Collection[V]].defaultRepValue, element[Collection[E]].defaultRepValue, 0)
    override lazy val tag = {
      implicit val tagV = eV.tag
      implicit val tagE = eE.tag
      weakTypeTag[IncidenceGraph[V, E]]
    }
  }

  // state representation type
  type IncidenceGraphData[V, E] = (Collection[V], (Collection[E], Int))

  // 3) Iso for concrete class
  class IncidenceGraphIso[V, E](implicit eV: Elem[V], eE: Elem[E])
    extends EntityIso[IncidenceGraphData[V, E], IncidenceGraph[V, E]] with Def[IncidenceGraphIso[V, E]] {
    override def from(p: Rep[IncidenceGraph[V, E]]) =
      (p.vertexValues, p.incMatrixWithVals, p.vertexNum)
    override def to(p: Rep[(Collection[V], (Collection[E], Int))]) = {
      val Pair(vertexValues, Pair(incMatrixWithVals, vertexNum)) = p
      IncidenceGraph(vertexValues, incMatrixWithVals, vertexNum)
    }
    lazy val eFrom = pairElement(element[Collection[V]], pairElement(element[Collection[E]], element[Int]))
    lazy val eTo = new IncidenceGraphElem[V, E](self)
    lazy val selfType = new IncidenceGraphIsoElem[V, E](eV, eE)
    def productArity = 2
    def productElement(n: Int) = n match {
      case 0 => eV
      case 1 => eE
    }
  }
  case class IncidenceGraphIsoElem[V, E](eV: Elem[V], eE: Elem[E]) extends Elem[IncidenceGraphIso[V, E]] {
    def getDefaultRep = reifyObject(new IncidenceGraphIso[V, E]()(eV, eE))
    lazy val tag = {
      implicit val tagV = eV.tag
      implicit val tagE = eE.tag
      weakTypeTag[IncidenceGraphIso[V, E]]
    }
    lazy val typeArgs = TypeArgs("V" -> (eV -> scalan.util.Invariant), "E" -> (eE -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class IncidenceGraphCompanionAbs extends CompanionDef[IncidenceGraphCompanionAbs] with IncidenceGraphCompanion {
    def selfType = IncidenceGraphCompanionElem
    override def toString = "IncidenceGraph"
    @scalan.OverloadId("fromData")
    def apply[V, E](p: Rep[IncidenceGraphData[V, E]])(implicit eV: Elem[V], eE: Elem[E]): Rep[IncidenceGraph[V, E]] =
      isoIncidenceGraph(eV, eE).to(p)
    @scalan.OverloadId("fromFields")
    def apply[V, E](vertexValues: Coll[V], incMatrixWithVals: Coll[E], vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[IncidenceGraph[V, E]] =
      mkIncidenceGraph(vertexValues, incMatrixWithVals, vertexNum)

    def unapply[V, E](p: Rep[Graph[V, E]]) = unmkIncidenceGraph(p)
  }
  lazy val IncidenceGraphRep: Rep[IncidenceGraphCompanionAbs] = new IncidenceGraphCompanionAbs
  lazy val IncidenceGraph: IncidenceGraphCompanionAbs = proxyIncidenceGraphCompanion(IncidenceGraphRep)
  implicit def proxyIncidenceGraphCompanion(p: Rep[IncidenceGraphCompanionAbs]): IncidenceGraphCompanionAbs = {
    proxyOps[IncidenceGraphCompanionAbs](p)
  }

  implicit case object IncidenceGraphCompanionElem extends CompanionElem[IncidenceGraphCompanionAbs] {
    lazy val tag = weakTypeTag[IncidenceGraphCompanionAbs]
    protected def getDefaultRep = IncidenceGraph
  }

  implicit def proxyIncidenceGraph[V, E](p: Rep[IncidenceGraph[V, E]]): IncidenceGraph[V, E] =
    proxyOps[IncidenceGraph[V, E]](p)

  implicit class ExtendedIncidenceGraph[V, E](p: Rep[IncidenceGraph[V, E]])(implicit eV: Elem[V], eE: Elem[E]) {
    def toData: Rep[IncidenceGraphData[V, E]] = isoIncidenceGraph(eV, eE).from(p)
  }

  // 5) implicit resolution of Iso
  implicit def isoIncidenceGraph[V, E](implicit eV: Elem[V], eE: Elem[E]): Iso[IncidenceGraphData[V, E], IncidenceGraph[V, E]] =
    reifyObject(new IncidenceGraphIso[V, E]()(eV, eE))

  // 6) smart constructor and deconstructor
  def mkIncidenceGraph[V, E](vertexValues: Coll[V], incMatrixWithVals: Coll[E], vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[IncidenceGraph[V, E]]
  def unmkIncidenceGraph[V, E](p: Rep[Graph[V, E]]): Option[(Rep[Collection[V]], Rep[Collection[E]], Rep[Int])]

  registerModule(Graphs_Module)
}

// Std -----------------------------------
trait GraphsStd extends scalan.ScalanDslStd with GraphsDsl {
  self: GraphsDslStd =>

  lazy val Graph: Rep[GraphCompanionAbs] = new GraphCompanionAbs {
  }

  case class StdAdjacencyGraph[V, E]
      (override val vertexValues: Coll[V], override val edgeValues: NColl[E], override val links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E])
    extends AbsAdjacencyGraph[V, E](vertexValues, edgeValues, links) {
  }

  def mkAdjacencyGraph[V, E]
    (vertexValues: Coll[V], edgeValues: NColl[E], links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[AdjacencyGraph[V, E]] =
    new StdAdjacencyGraph[V, E](vertexValues, edgeValues, links)
  def unmkAdjacencyGraph[V, E](p: Rep[Graph[V, E]]) = p match {
    case p: AdjacencyGraph[V, E] @unchecked =>
      Some((p.vertexValues, p.edgeValues, p.links))
    case _ => None
  }

  case class StdIncidenceGraph[V, E]
      (override val vertexValues: Coll[V], override val incMatrixWithVals: Coll[E], override val vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E])
    extends AbsIncidenceGraph[V, E](vertexValues, incMatrixWithVals, vertexNum) {
  }

  def mkIncidenceGraph[V, E]
    (vertexValues: Coll[V], incMatrixWithVals: Coll[E], vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[IncidenceGraph[V, E]] =
    new StdIncidenceGraph[V, E](vertexValues, incMatrixWithVals, vertexNum)
  def unmkIncidenceGraph[V, E](p: Rep[Graph[V, E]]) = p match {
    case p: IncidenceGraph[V, E] @unchecked =>
      Some((p.vertexValues, p.incMatrixWithVals, p.vertexNum))
    case _ => None
  }
}

// Exp -----------------------------------
trait GraphsExp extends scalan.ScalanDslExp with GraphsDsl {
  self: GraphsDslExp =>

  lazy val Graph: Rep[GraphCompanionAbs] = new GraphCompanionAbs {
  }

  case class ExpAdjacencyGraph[V, E]
      (override val vertexValues: Coll[V], override val edgeValues: NColl[E], override val links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E])
    extends AbsAdjacencyGraph[V, E](vertexValues, edgeValues, links)

  object AdjacencyGraphMethods {
    object incMatrix {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "incMatrix" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object incMatrixWithVals {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "incMatrixWithVals" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object vertexNum {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "vertexNum" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edgeNum {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "edgeNum" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inDegrees {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "inDegrees" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outDegrees {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "outDegrees" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object makeEdgeFrom {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, iE, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "makeEdgeFrom" =>
          Some((receiver, v, iE)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object makeEdgeFromTo {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1, v2, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "makeEdgeFromTo" =>
          Some((receiver, v1, v2)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object nodes {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "nodes" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edges {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "edges" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outEdges {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, predicate, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "outEdges" =>
          Some((receiver, vs, predicate)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inNeighbors {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "inNeighbors" =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outNeighborsOf_1 {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "outNeighborsOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "1" } =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outNeighborsOf_2 {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Coll[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "outNeighborsOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "2" } =>
          Some((receiver, vs)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Coll[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Coll[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object commonNbrs {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1Id, v2Id, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "commonNbrs" =>
          Some((receiver, v1Id, v2Id)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object commonNbrsNum {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1Id, v2Id, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "commonNbrsNum" =>
          Some((receiver, v1Id, v2Id)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object hasEdgeTo {
      def unapply(d: Def[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(fromId, toId, _*), _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "hasEdgeTo" =>
          Some((receiver, fromId, toId)).asInstanceOf[Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[AdjacencyGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object discardValues {
      def unapply(d: Def[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AdjacencyGraphElem[_, _]] && method.getName == "discardValues" =>
          Some(receiver).asInstanceOf[Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[AdjacencyGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object AdjacencyGraphCompanionMethods {
    object fromAdjacencyList {
      def unapply(d: Def[_]): Option[(Coll[V], NColl[E], NColl[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vertexValues, edgeValues, links, _*), _) if receiver.elem == AdjacencyGraphCompanionElem && method.getName == "fromAdjacencyList" =>
          Some((vertexValues, edgeValues, links)).asInstanceOf[Option[(Coll[V], NColl[E], NColl[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Coll[V], NColl[E], NColl[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  def mkAdjacencyGraph[V, E]
    (vertexValues: Coll[V], edgeValues: NColl[E], links: NColl[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[AdjacencyGraph[V, E]] =
    new ExpAdjacencyGraph[V, E](vertexValues, edgeValues, links)
  def unmkAdjacencyGraph[V, E](p: Rep[Graph[V, E]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: AdjacencyGraphElem[V, E] @unchecked =>
      Some((p.asRep[AdjacencyGraph[V, E]].vertexValues, p.asRep[AdjacencyGraph[V, E]].edgeValues, p.asRep[AdjacencyGraph[V, E]].links))
    case _ =>
      None
  }

  case class ExpIncidenceGraph[V, E]
      (override val vertexValues: Coll[V], override val incMatrixWithVals: Coll[E], override val vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E])
    extends AbsIncidenceGraph[V, E](vertexValues, incMatrixWithVals, vertexNum)

  object IncidenceGraphMethods {
    object incMatrix {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "incMatrix" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edgeNum {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "edgeNum" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inDegrees {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "inDegrees" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outDegrees {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "outDegrees" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object makeEdgeFrom {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, iE, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "makeEdgeFrom" =>
          Some((receiver, v, iE)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object makeEdgeFromTo {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1, v2, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "makeEdgeFromTo" =>
          Some((receiver, v1, v2)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object nodes {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "nodes" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edges {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "edges" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object rowIndexes {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "rowIndexes" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object vertexRow {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "vertexRow" =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object vertexNonZeroRow {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "vertexNonZeroRow" =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object links {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "links" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edgeValues {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "edgeValues" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outEdges {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, predicate, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "outEdges" =>
          Some((receiver, vs, predicate)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inNeighbors {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "inNeighbors" =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outNeighborsOf_1 {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "outNeighborsOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "1" } =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outNeighborsOf_2 {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Coll[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "outNeighborsOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "2" } =>
          Some((receiver, vs)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Coll[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Coll[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outEdgesOf1 {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "outEdgesOf1" =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object commonNbrs {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1Id, v2Id, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "commonNbrs" =>
          Some((receiver, v1Id, v2Id)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object commonNbrsNum {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1Id, v2Id, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "commonNbrsNum" =>
          Some((receiver, v1Id, v2Id)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object hasEdgeTo {
      def unapply(d: Def[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(fromId, toId, _*), _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "hasEdgeTo" =>
          Some((receiver, fromId, toId)).asInstanceOf[Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[IncidenceGraph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object discardValues {
      def unapply(d: Def[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[IncidenceGraphElem[_, _]] && method.getName == "discardValues" =>
          Some(receiver).asInstanceOf[Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[IncidenceGraph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object IncidenceGraphCompanionMethods {
    object fromAdjacencyMatrix {
      def unapply(d: Def[_]): Option[(Coll[V], Coll[E], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vertexValues, incMatrixWithVals, vertexNum, _*), _) if receiver.elem == IncidenceGraphCompanionElem && method.getName == "fromAdjacencyMatrix" =>
          Some((vertexValues, incMatrixWithVals, vertexNum)).asInstanceOf[Option[(Coll[V], Coll[E], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Coll[V], Coll[E], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  def mkIncidenceGraph[V, E]
    (vertexValues: Coll[V], incMatrixWithVals: Coll[E], vertexNum: Rep[Int])(implicit eV: Elem[V], eE: Elem[E]): Rep[IncidenceGraph[V, E]] =
    new ExpIncidenceGraph[V, E](vertexValues, incMatrixWithVals, vertexNum)
  def unmkIncidenceGraph[V, E](p: Rep[Graph[V, E]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: IncidenceGraphElem[V, E] @unchecked =>
      Some((p.asRep[IncidenceGraph[V, E]].vertexValues, p.asRep[IncidenceGraph[V, E]].incMatrixWithVals, p.asRep[IncidenceGraph[V, E]].vertexNum))
    case _ =>
      None
  }

  object GraphMethods {
    object makeEdgeFrom {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, iE, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "makeEdgeFrom" =>
          Some((receiver, v, iE)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object makeEdgeFromTo {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1, v2, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "makeEdgeFromTo" =>
          Some((receiver, v1, v2)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object thisGraph {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "thisGraph" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object vertexValues {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "vertexValues" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edgeValues {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "edgeValues" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object links {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "links" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object incMatrix {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "incMatrix" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object incMatrixWithVals {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "incMatrixWithVals" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object discardValues {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "discardValues" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object addEdges {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[EdgeList]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(el, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "addEdges" =>
          Some((receiver, el)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[EdgeList]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[EdgeList]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inverted {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "inverted" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object complement {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "complement" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object vertexNum {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "vertexNum" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edgeNum {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "edgeNum" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inDegrees {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "inDegrees" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outDegrees {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "outDegrees" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object getNode {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vId, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "getNode" =>
          Some((receiver, vId)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object getNodes {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Coll[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vIds, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "getNodes" =>
          Some((receiver, vIds)).asInstanceOf[Option[(Rep[Graph[V, E]], Coll[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Coll[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object nodes {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "nodes" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object simpleNodes {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "simpleNodes" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object edges {
      def unapply(d: Def[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "edges" =>
          Some(receiver).asInstanceOf[Option[Rep[Graph[V, E]] forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Graph[V, E]] forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object inNeighbors {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "inNeighbors" =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outNeighborsOf_1 {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "outNeighborsOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "1" } =>
          Some((receiver, v)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outNeighborsOf_2 {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Coll[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "outNeighborsOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "2" } =>
          Some((receiver, vs)).asInstanceOf[Option[(Rep[Graph[V, E]], Coll[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Coll[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outEdgesOf_1 {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Coll[Int], Rep[BitSet]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, excluding, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "outEdgesOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "1" } =>
          Some((receiver, vs, excluding)).asInstanceOf[Option[(Rep[Graph[V, E]], Coll[Int], Rep[BitSet]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Coll[Int], Rep[BitSet]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outEdgesOf_2 {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Front]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(fr, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "outEdgesOf" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "2" } =>
          Some((receiver, fr)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Front]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Front]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object outEdges {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(vs, predicate, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "outEdges" =>
          Some((receiver, vs, predicate)).asInstanceOf[Option[(Rep[Graph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Coll[Int], Rep[Edge[V, E] => Boolean]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object hasEdgeTo {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(fromId, toId, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "hasEdgeTo" =>
          Some((receiver, fromId, toId)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object commonNbrs {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1Id, v2Id, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "commonNbrs" =>
          Some((receiver, v1Id, v2Id)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object commonNbrsNum {
      def unapply(d: Def[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = d match {
        case MethodCall(receiver, method, Seq(v1Id, v2Id, _*), _) if receiver.elem.isInstanceOf[GraphElem[_, _, _]] && method.getName == "commonNbrsNum" =>
          Some((receiver, v1Id, v2Id)).asInstanceOf[Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[(Rep[Graph[V, E]], Rep[Int], Rep[Int]) forSome {type V; type E}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object GraphCompanionMethods {
  }
}

object Graphs_Module extends scalan.ModuleInfo {
  val dump = "H4sIAAAAAAAAANVXTWwbRRQe23EcxyGkQTSlUkkILv/YUSJaUIRQSJySynGibpOAWxWNdyfOpLuzy+44tTmUWyXghhAHBIciEJcICXFBReoFkBBCPXDlzKmlqnqgEhKIN7M/Xie7DoH2wB5GO3/v53vfezOz/RtKOzZ6zFGxjlnBIBwXFPk/4/C8UmKc8taiqTV0MkfWz73yyR9njLdHkmioino3sDPn6FWUdX9KTSv4V7hWRlnMVOJw03Y4eqQsNRRVU9eJyqnJitQwGhzXdFIsU4dPl1FPzdRab6CLKFFGQ6rJVJtwoszq2HGI4433EWERDfpZ2W8tWW0drCi8KIa8OG1jysF80DHkrj9FLKXFTNYyOBr0TFuyhFmwJkeaFviwYFi6VJMqoww1LNPmvtYMaNgwNb/bwzAMoOHyJt7CRdBaLyrcpqwuhFlYPY/rpAJLxPIe8MEh+vrplkU84TmHax36mhZCCKIyKQ0rtDErBJgVBGZ5hdgU6/RNLCaXbbPZQu6XSCHUtEDEM3uI8CWQEtPy75xVz9xRckZSbG4KUzLSoF4QNBrDEBkewPaHU+85t09cPp5E/VXUT52ZmsNtrPIwDTy4cpgxk0ubAwSxXYcIjsdFUGqZgTU7aJJVTcPCDCR5WA5AoHSqUi4Wi7EBLzwx2Ge4RfyliaaVCPwdi/FXcmkW6/ry9YeePXqj9GoSJTtVZEGkAslg+0I5Sp+wsbXhyRbt/RwlVtsAi25JdkWTbbbbTBdTAlAev35T+34CnU0GUHqa/1n0QMTw8x9eOUqWv0iivqok+7yO6zKOAqs54qhV1GduEdsdz2xhXfxFxjKjkXXc0LmHcBiaFEDD0VhsnlpE4DYt+Z/w3c+5FK6YjOTnl/O/Kz++vy0YaqMBd8ZN3L/o8T9/GVznkrwcDYCxnDRXsd4gjg9yzyzkQWQYXMTl4OFAuWhGOeonWp10CkpXIiSV9pSU1ik7Hy3ERo/GMc4iyzY1oOptkee+/Wbl1tVKWpJu2ANamuYWHA/nNuYCisQER6kFxqO41e9CqJgGOTB+m567/C6XLEo0O+vZUm0T6se03PdwF0L5pfbLS5cevPXp6w/IctBXo9zAVn5iH8XAz917mOyoM36Ds96RI5NicsfkjLaJVcLUlkzlmFwV7Ugw50YdAjDSuXk27MZoaGdI5eHEDuokyWrA4pJOjD1ZvFtAqZuA3eUn0unRgK5H4ukKGL92QyscunnkQhL1nkTpdagVThmla2aDaX7w4ISHBOUv+2OJzuBBsLCNjeDg38JwUgF5ODro148Gp3px1Rt3qwZ8Y6iNa7vM2uigZ7DYVVhgrjyef/rr7Qv02pPzsm5IbSuRctoAVUMw5xKdOP6XSr+LAzvLx12saQcoUxcx1IzmGuUbILGrvL0rW9Y1rdIw/E0puGO5YkQzFe3uMdm+sK9MXGAq1YCU5N9kYufm/08m7nZ6NLQtmrFtS6r74XXI9N7I8KSgvt8t1sfEqws5cqKgz2OD6q3JKCv2R4jBOB5YYXn3GGvRftTWcwyKVSGmus4RVcc20cRjgRjwmHFPzqkPXlo7eWhtRZ7dA5pc5M4E163op9citqblQ+GJLg8FWJQvGRY8BOFn6rsXf37rp88/k/WyDShHvS4JOLrPs70u+4FL4zEuKd4hDay6eOfjylPXvvpVXm/6xXEPlz4WPLvC15rO0Gdd3fCKCoUf0ktcAEJcuiKaq38DrwCfyPQOAAA="
}
}


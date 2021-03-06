package scalan.meta

/**
  * Created by slesarenko on 23/02/15.
  */
import ScalanAst._
import PrintExtensions._
import scalan.util.GraphUtil

object ScalanAstExtensions {

  implicit class SMethodOrClassArgOps(arg: SMethodOrClassArg) {
    def unrepType(module: SUnitDef) =
      if (module.isVirtualized) arg.tpe.unRep(module, module.isVirtualized)
      else arg.tpe
  }

  implicit class SMethodOrClassArgsOps(as: SMethodOrClassArgs) {
    def argNames = as.args.map(a => a.name)

    def argNamesAndTypes(config: UnitConfig) = {
      as.args.map { arg =>
        if (config.isVirtualized || arg.isTypeDesc)
          s"${arg.name}: ${arg.tpe}"
        else
          s"${arg.name}: Rep[${arg.tpe}]"
      }
    }

    def argUnrepTypes(module: SUnitDef, isVirtualized: Boolean) = {
      if (isVirtualized) {
        as.args.map({ a =>
          val res = a.tpe.unRep(module, isVirtualized)
          res.getOrElse { sys.error(s"Invalid field $a. Fields of concrete classes should be of type Rep[T] for some T.") }
        })
      }
      else
        as.args.map(_.tpe)
    }
  }

  implicit class SMethodArgsListOps(sections: List[SMethodArgs]) {
    def splitArgSections(): (List[SMethodArgs], List[SMethodArgs]) = {
      sections partition {
        _ match {
          case SMethodArgs((arg: SMethodOrClassArg) :: _) => arg.impFlag
          case _ => false
        }
      }
    }
    def joinArgSections() = {
      val newSingleSection = sections.flatMap(_.args)
      List(SMethodArgs(newSingleSection))
    }
  }

  implicit class STpeArgsOps(args: STpeArgs) {
    def decls = args.map(_.declaration)

    def names = args.map(_.name)

    def declString = decls.asTypeParams()

    def useString = names.asTypeParams()

    def indexByName(argName: String): Int = {
      args.zipWithIndex.find { case (a, i) => a.name == argName } match {
        case Some((_, i)) => i
        case None => -1
      }
    }

    def getBoundedTpeArgString(withTags: Boolean = false, methodArgs: List[SMethodArgs] = Nil) = {
      def getElem(tpeArg: STpeArg) = {
        if (tpeArg.hasElemBound(methodArgs)) s"${tpeArg.name}"
        else s"${tpeArg.name}:Elem"
      }

      def getCont(tpeArg: STpeArg) = {
        if (tpeArg.hasContBound(methodArgs)) s"${tpeArg.declaration}"
        else s"${tpeArg.declaration}:Cont"
      }

      def getWeakTypeTag(tpeArg: STpeArg) = {
        if (tpeArg.hasWeakTypeTagBound(methodArgs)) ""
        else withTags.opt(":WeakTypeTag")
      }

      args.asTypeParams { t =>
        (if (t.isHighKind) getCont(t) else getElem(t)) + getWeakTypeTag(t)
      }
    }
  }

  implicit class STpeDefOps(td: STpeDef) {
    def declaration = s"type ${td.name}${td.tpeArgs.declString} = ${td.tpe}"
  }

  implicit class SMethodDefOps(md: SMethodDef) {
    def explicitReturnType(config: UnitConfig): String = {
      def error = throw new IllegalStateException(s"Explicit return type required for method $md")

      val tRes = md.tpeRes.getOrElse(error)
      if (config.isVirtualized) tRes.toString
      else s"Rep[$tRes]"
    }

    def declaration(config: UnitConfig, includeOverride: Boolean) = {
      val typesDecl = md.tpeArgs.declString
      val argss = md.argSections.rep(sec => s"(${sec.argNamesAndTypes(config).rep()})", "")
      s"${includeOverride.opt("override ")}def ${md.name}$typesDecl$argss: ${explicitReturnType(config)}"
    }
  }

  implicit class SUnitDefOps(unit: SUnitDef) {
    implicit val ctx = unit.context
    def packageAndName = s"${unit.packageName}.${unit.name}"
    def fileName = s"${unit.packageName.replace('.','/')}/${unit.name}.scala"
    def unitFileName = unit.name + ".scala"
    def selfTypeString(suffix: String) =
      unit.selfType.opt(t => s"self: ${t.tpe}${suffix} =>")

    /** Returns traits and classes of this unit topologically sorted by inheritance (ancestors first). */
    def allEntitiesSorted: List[SEntityDef] = {
      val localEntityNames = unit.allEntities.map(_.name).toSet
      def inherit(n: String) = {
        val e = unit.getEntity(n)
        val as = e.getAncestorTypeNames.filter(n => localEntityNames.contains(n))
        as
      }
      val es = GraphUtil.stronglyConnectedComponents(unit.allEntities.map(_.name))(inherit).flatten
      es.map(unit.getEntity).toList
    }

    def updateFirstEntity(updater: STraitDef => STraitDef) = unit.traits.headOption match {
      case Some(e) =>
        val newEntity = updater(unit.traits.head)
        unit.copy(traits = newEntity :: unit.traits.tail)(ctx)
      case None => unit
    }

    def updateEntities(updater: STraitDef => STraitDef) = {
      val newTraits = unit.traits.map(updater)
      unit.copy(traits = newTraits)(ctx)
    }
  }

}

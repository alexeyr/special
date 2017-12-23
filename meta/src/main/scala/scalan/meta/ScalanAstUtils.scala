package scalan.meta

import scalan.meta.ScalanAst._
import scalan.meta.Base._
import scalan.meta.ScalanAstTransformers.{AstTransformer, TypeTransformer, AstReplacer}
import scalan.util.CollectionUtil._

object ScalanAstUtils {

  /** Appends the given suffix to the type of self. For example:
    *   trait Matrs {self: LinearAlgebra => ... }
    * and for the suffix "Dsl", the method extends LinearAlgebra to LinearAlgebraDsl:
    *   trait Matrs {self: LinearAlgebraDsl => ... }
    * If the module doesn't have self than self is added with the type of module plus the suffix:
    *   trait Matrs {...} -> trait Matrs {self: MatrsDsl => ...}
    * */
  def selfComponentsWithSuffix(moduleName: String,
                               selfType: Option[SSelfTypeDef],
                               suffix: String): List[STpeExpr] = {
    selfType match {
      case Some(selfTypeDef) => selfTypeDef.components.map {
        case tr: STraitCall => tr.copy(name = tr.name + suffix)
        case c => c
      }
      case _ => List(STraitCall(moduleName + suffix, List()))
    }
  }

  def selfModuleComponents(module: SUnitDef, suffix: String): List[STpeExpr] = {
    selfComponentsWithSuffix(module.name, module.selfType, suffix)
  }

  /** Creates empty companion trait (no body) for an entity or concrete classes. */
  def createCompanion(traitName: String): STraitDef = STraitDef(
    name = traitName + "Companion",
    tpeArgs = List(),
    ancestors = List(),
    body = List(),
    selfType = None,
    companion = None
  )

  /**
    * Checks if companion is represented by SObjectDef (e.g. parsed from object)
    * and converts into the companion trait (STraitDef).
    */
  def convertCompanion(comp: SEntityDef): SEntityDef = comp match {
    case obj: SObjectDef =>
      STraitDef(name = obj.name + "Companion",
        tpeArgs = obj.tpeArgs, ancestors = obj.ancestors, body = obj.body, selfType = obj.selfType,
        companion = obj.companion, annotations = obj.annotations)
    case _ => comp
  }

  def firstKindArgs(tpeArgs: List[STpeArg]): List[STpeArg] = {
    tpeArgs.filter(_.tparams.isEmpty)
  }

  def highKindArgs(tpeArgs: List[STpeArg]): List[STpeArg] = {
    tpeArgs.filter(!_.tparams.isEmpty)
  }

  def genImplicitMethod(tpeArg: STpeArg, methodPrefix: String, descTypeName: String) =
    SMethodDef(name = methodPrefix + tpeArg.name,
      tpeArgs = Nil, argSections = Nil,
      tpeRes = Some(STraitCall(descTypeName, List(STraitCall(tpeArg.name, Nil)))),
      isImplicit = true, isOverride = false,
      overloadId = None, annotations = Nil,
      body = None, isTypeDesc = true)

  def genElemMethod(tpeArg: STpeArg) = genImplicitMethod(tpeArg, "e", "Elem")
  def genContMethod(tpeArg: STpeArg) = genImplicitMethod(tpeArg, "c", "Cont")

  def genDescMethodsByTypeArgs(tpeArgs: List[STpeArg]): List[SMethodDef] = {
    tpeArgs.map{ targ =>
      if (!targ.isHighKind) genElemMethod(targ)
      else if (targ.tparams.size == 1) genContMethod(targ)
      else !!!(s"Cannot create descriptor method for a high-kind tpeArg $targ " +
      s"with with more than one type arguments ${targ.tparams}. Only single argument supported.")
    }
  }

  def genClassArg(argPrefix: String, argName: String, descName: String, descArg: STpeExpr) = {
    SClassArg(impFlag = true,
      overFlag = false, valFlag = true,
      name = argPrefix + argName,
      tpe = STraitCall(descName, List(descArg)),
      default = None, annotations = Nil, isTypeDesc = true)
  }
  def genElemClassArg(argName: String, tpe: STpeExpr) = genClassArg("e", argName, "Elem", tpe)
  def genContClassArg(argName: String, tpe: STpeExpr) = genClassArg("c", argName, "Cont", tpe)

  def genImplicitClassArg(isHighKind: Boolean, argName: String, tpe: STpeExpr): SClassArg = {
    if (!isHighKind) genElemClassArg(argName, tpe)
    else genContClassArg(argName, tpe)
  }
  def genImplicitClassArg(tyArg: STpeArg): SClassArg =
    genImplicitClassArg(tyArg.isHighKind, tyArg.name, STraitCall(tyArg.name)) // don't use toTraitCall here

  /** See example to understand the code:
    * trait <e.name>[<e.tpeArgs>] { }
    * <clazz> == class <clazz>[<clsTpeArg>..] extends <ancName>[<ancArgs>]
    * Returns substitution for each type arg of each ancestor (e, A0) -> ancArgs(0) ... (e, An) -> ancArgs(n)
    */
  def argsSubstOfAncestorEntities(clazz: SEntityDef)(implicit ctx: AstContext): List[((SEntityDef, STpeArg), STpeExpr)] = {
    val res = clazz.ancestors.flatMap { anc =>
      val ancName = anc.tpe.name
      val ancestorEnt_? = ctx.findModuleEntity(ancName).map(_._2)
      ancestorEnt_? match {
        case Some(e) =>
          val ancArgs = anc.tpe.args
          e.zipWithExpandedBy(_.tpeArgs) zip ancArgs
        case None =>
          List[((SEntityDef, STpeArg), STpeExpr)]()
      }
    }
    res
  }

  /** Checks for each type argument if it is used as argument of ancestor entity.
    * For each name of type argument returns a pair (e, tyArg)
    */
  def classArgsAsSeenFromAncestors(entity: SEntityDef)(implicit ctx: AstContext) = {
    val subst: List[((SEntityDef, STpeArg), STpeExpr)] = argsSubstOfAncestorEntities(entity)
    val res = entity.tpeArgs.map { clsTpeArg =>
//      val argTpe = STraitCall(clsTpeArg.name) // don't use toTraitCall here
//      val substOpt = subst.find { case ((e, eTpeArg), ancArg) => argTpe == ancArg }
//      substOpt match {
//        case Some(((e, eTpeArg), ancArg)) => // clsTpeArg is used as argument of at least one ancestor
//          (clsTpeArg, (e, eTpeArg))
//        case None =>
          (clsTpeArg, (entity, clsTpeArg))
//      }
    }
    res
  }

  /** See example to understand the code:
    * trait <e.name>[<e.tpeArgs>] { }
    * <clazz> == class <clazz>[<clsTpeArg>..] extends <ancName>[<ancArgs>]
    */
  def genImplicitArgsForClass(clazz: SEntityDef)(implicit ctx: AstContext): List[SClassArg] = {
    val argSubst = classArgsAsSeenFromAncestors(clazz)
    val implicitArgs = argSubst.map { case (clsTpeArg, (e, eTpeArg)) =>
      genImplicitClassArg(eTpeArg.isHighKind, eTpeArg.name, STraitCall(clsTpeArg.name))
    }
    implicitArgs
  }

  def genImplicitMethodArg(tpeArg: STpeArg, valPrefix: String, descTypeName: String) =
    SMethodArg(impFlag = true, overFlag = false,
      name = valPrefix + tpeArg.name,
      tpe = STraitCall(descTypeName, List(STraitCall(tpeArg.name, Nil))),
      default = None, annotations = Nil, isTypeDesc = true)
  def genElemMethodArg(tpeArg: STpeArg) = genImplicitMethodArg(tpeArg, "em", "Elem")
  def genContMethodArg(tpeArg: STpeArg) = genImplicitMethodArg(tpeArg, "cm", "Cont")
  def genImplicitVals(tpeArgs: List[STpeArg]): List[SMethodArg] = {
    tpeArgs.map { arg =>
      if (!arg.isHighKind) genElemMethodArg(arg)
      else genContMethodArg(arg)
    }
  }

  /** According to scala docs, a method or constructor can have only one implicit parameter list,
    * and it must be the last parameter list given. */
  def joinImplicitArgs(argSections: List[SMethodArgs]): List[SMethodArgs] = {
    val cleanArgs = argSections.map(_.args)
    val (imp, nonImp) = cleanArgs.partition {
      case (m: SMethodArg) :: _ => m.impFlag
      case _ => false
    }
    val newArgs = imp.flatten match {
      case Nil => nonImp
      case as => nonImp ++ List(as)
    }
    newArgs.map(args => SMethodArgs(args))
  }

  /** Takes type arguments of the method and either add new section with implicits descriptor vals
    * or add them to existing implicit section */
  def genImplicitMethodArgs(module: SUnitDef, method: SMethodDef): SMethodDef = {
    val newSections = genImplicitVals(method.tpeArgs) match {
      case Nil => method.argSections
      case as => method.argSections ++ List(SMethodArgs(as))
    }
    method.copy(argSections = joinImplicitArgs(newSections))
  }

  def tpeUseExpr(arg: STpeArg): STpeExpr = STraitCall(arg.name, arg.tparams.map(tpeUseExpr))

}

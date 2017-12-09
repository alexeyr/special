package scalan.plugin

import java.io.File

import scala.reflect.io.{PlainFile, Path}
import scalan.meta.scalanizer.Scalanizer
import scala.tools.nsc.Global
import scalan.meta.ScalanAst._
import scalan.meta.ScalanAstExtensions._
import scalan.meta._
import scalan.util.FileUtil
import scala.collection.mutable.{Map => MMap}

class TargetModulePipeline[+G <: Global](s: Scalanizer[G]) extends ScalanizerPipeline[G](s) {
  import scalanizer._
  import scalanizer.global._

  val name = "target-assembler"
  val runAfter = List("parser")
  val wrappers = MMap[SName, SUnitDef]()
  val preparedUnits = MMap[SName, (SUnitDef, UnitConfig)]()

  override def isEnabled: Boolean = {
    val moduleName = s.moduleName
    s.snConfig.targetModules.get(moduleName).isDefined
  }

  def copyFile(sourceFile: File, targetFile: File): Boolean = {
    val isNewFile = !targetFile.exists
    scalanizer.inform(s"Copying from $sourceFile to $targetFile ...")
    FileUtil.copy(sourceFile, targetFile)
    isNewFile
  }

  val moduleBuilder = new SModuleBuilder()(context)

  def prepareSourceUnit(unitConf: UnitConfig, target: TargetModuleConf) = {
    val sourceFile = unitConf.getResourceFile
    implicit val parseCtx = new ParseCtx(isVirtualized = true)(context)

    val sourceUnit = parseEntityModule(sourceFile)

    val preparedUnit = moduleBuilder.setSelfType(target.name.capitalize)(sourceUnit)
    preparedUnit
  }

  def copyScalanizedUnit(preparedUnit: SUnitDef, unitConf: UnitConfig, target: TargetModuleConf): Unit = {
    val targetSrcRoot = s"${target.name }/${ModuleConf.SourcesDir }"
    val targetSrcFile = FileUtil.file(targetSrcRoot, unitConf.entityFile)
    val isNewTargetFile = !targetSrcFile.exists

    val optImplicits = optimizeModuleImplicits(preparedUnit)
    implicit val genCtx = new GenCtx(context, isVirtualized = true, toRep = true)
    val unitTree = genPackageDef(optImplicits)
    saveCode(targetSrcRoot, optImplicits.packageName, optImplicits.name, ".scala", showCode(unitTree))

//    val transforms = Seq((u: SUnitDef) => moduleBuilder.genClassesImplicits(u))
//    val enriched = scala.Function.chain(transforms)(preparedUnit)

    /** produce boilerplate code using ModuleFileGenerator
      * NOTE: we need original preparedUnit with all implicits in there place for correct boilerplate generation */
    val boilerplateText = genUnitBoilerplateText(target, preparedUnit, isVirtualized = true)
    val targetImpl = saveImplCode(targetSrcRoot, preparedUnit.packageName, preparedUnit.name, ".scala", boilerplateText)
    val isNewImpl = !targetImpl.exists

    if (isNewTargetFile)
      global.currentRun.compileLate(new PlainFile(Path(targetSrcFile)))
    if (isNewImpl)
      global.currentRun.compileLate(new PlainFile(Path(targetImpl)))
  }

  def mergeWrapperUnit(unit: SUnitDef) = {
    val wName = SName(unit.packageName, unit.name)
    wrappers.get(wName) match {
      case Some(existingUnit) =>
        val merger = new SUnitMerger(existingUnit)(scalanizer.context)
        val newUnit =merger.merge(unit)
        wrappers(wName) = newUnit
      case None =>
        wrappers(wName) = unit
    }
  }

  def saveWrappersCake(sourceRoot: String, cake: WrapperCake) = {
    implicit val ctx = GenCtx(context = scalanizer.context, isVirtualized = true, toRep = false)
    val imports =
      genImport(SImportStat("scalan._")) ::
      cake.wrappers.map(w => genImport(SImportStat(w.fullName + "Module")))
    val cakeTree = genTrait(cake.traitDef)
    val pkgStats = imports :+ cakeTree
    val wrappersPackage = PackageDef(RefTree(Ident(TermName("scala")), TermName("wrappers")), pkgStats)
    val code = showCode(wrappersPackage)
    saveCode(sourceRoot, "scala.wrappers", cake.traitDef.name, ".scala", code)
  }

  val steps: List[PipelineStep] = List(
    RunStep("assembler") { _ =>
      scalanizer.inform(s"Processing target module '${scalanizer.moduleName }'")
      // merge all partial wrappers from source modules
      val target = snConfig.targetModules(moduleName)
      val sourceRoot = s"${target.name}/${ModuleConf.SourcesDir}"
      for (source <- target.sourceModules.values) {
        for (wFile <- source.listWrapperFiles) {
          val unit = parseEntityModule(wFile)(new ParseCtx(isVirtualized = true)(context))
          scalanizer.inform(s"Merging into wrapper ${unit.fullName} from $wFile")
          mergeWrapperUnit(unit)
        }
      }
      // 1) gen boilerplate and save for all merged wrappers
      // 2) build wrappers cake
      var wrappersCake = initWrapperCake()
      for (w <- wrappers.values) {
        val wPackage = genPackageDef(w, isVirtualized = true)(context)
        saveCode(sourceRoot, w.packageName, w.name, ".scala", showCode(wPackage))

        val boilerplateText = genUnitBoilerplateText(target, w, isVirtualized = true)
        saveImplCode(sourceRoot, w.packageName, w.name, ".scala", boilerplateText)

        wrappersCake = updateWrappersCake(wrappersCake, w)
      }

      // generate WrappersModule cake
      saveWrappersCake(sourceRoot, wrappersCake)

      // add wrappers to the context
      for (w <- wrappers.values) {
        val externalName = w.traits(0).getExternalName.get
        context.updateWrapper(externalName, WrapperDescr(w, Nil, WrapperConfig.default(externalName)))
      }

      // prepare units from source modules
      for (srcModule <- target.sourceModules.values) {
        for (srcUnitConf <- srcModule.units.values) {
          val prepared = prepareSourceUnit(srcUnitConf, target)
          context.addModule(prepared)
          val name = SName(prepared.packageName, prepared.name)
          preparedUnits += ((name, (prepared, srcUnitConf)))
        }
      }

      // generate CompilationUnit for each prepared unit from all source modules
      for ((_, (unit, conf)) <- preparedUnits) {
        copyScalanizedUnit(unit, conf, target)
      }
      ()
    }
  )
}

/**
 * User: Alexander Slesarenko
 * Date: 12/15/13
 */
package scalan.meta

import java.io.File

import com.typesafe.scalalogging.LazyLogging

import scalan.util.FileUtil
import ScalanAst._
import scala.tools.nsc.Global

class Parsers(val configs: List[UnitConfig]) extends ScalanParsersEx[Global] {
  def getGlobal: Global = new Global(settings, reporter)
  implicit val context = new AstContext(configs, this)
  initCompiler()
}

class EntityManagement[+G <: Global](val parsers: ScalanParsers[G]) extends LazyLogging {
  import parsers._
  def configs = parsers.context.configs
  implicit def context = parsers.context
   
  case class EntityManager(name: String, file: File, resourceFile: File, module: SUnitDef, config: UnitConfig)

  protected val entities = (for(c <- configs) yield {
    val file = c.getFile
    val resourceFile = c.getResourceFile
    try {
      val module = parseUnitFile(file)(new ParseCtx(c.isVirtualized))
      inform(s"Adding unit parsed from ${file} (relative to ${FileUtil.currentWorkingDir })")
      context.addUnit(module)
      Some((c.name, new EntityManager(module.name, file, resourceFile, module, c)))
    } catch {
      case e: Exception =>
        val msg = s"Failed to parse file at $file (relative to ${FileUtil.currentWorkingDir })"
        inform(msg)
        logger.error(msg, e)
        None
    }
  }).flatten.toMap

  def createFileGenerator(codegen: MetaCodegen, module: SUnitDef, config: UnitConfig) =
    new ModuleFileGenerator(codegen, module, config)

  val enrichPipeline = new ScalanAstTransformers.EnrichPipeline()

  def generate(configName: String) = {
    entities.get(configName) match {
      case Some(man) =>
        println(s"  generating ${man.file}")
        val enrichedModule = enrichPipeline(man.module)
        val g = createFileGenerator(ScalanCodegen, enrichedModule, man.config)

        val implCode = g.emitImplFile
        val implFile = UnitConfig.getImplFile(man.file, "Impl", "scala")
        FileUtil.write(implFile, implCode)
        FileUtil.copy(man.file, man.resourceFile)
      case None =>
        logger.error(s"Cannot generate code for config '$configName' because it is not found.")
    }
  }
}

package scalan.plugin

import scala.tools.nsc._
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scalan.meta.EntityManagement
import scalan.meta.ScalanAst.AstContext
import scalan.meta.scalanizer.{ScalanizerConfig, Scalanizer, ScalanizerState}

abstract class ScalanPlugin(val global: Global) extends Plugin { plugin =>
}

class ScalanizerPlugin(g: Global) extends ScalanPlugin(g) { plugin =>
  val scalanizer: Scalanizer[plugin.global.type] = new Scalanizer[plugin.global.type] {
    def getGlobal: plugin.global.type = plugin.global
    val snConfig: ScalanizerConfig = new ScalanizerPluginConfig
    val context: AstContext = new AstContext(snConfig.unitConfigs, this)
    val entityManagment: EntityManagement[plugin.global.type] = new EntityManagement(this)
    val snState: ScalanizerState[plugin.global.type] = new ScalanizerPluginState(this)
  }

  /** Visible name of the plugin */
  val name: String = "scalanizer"

  /** The compiler components that will be applied when running this plugin */
  val components: List[PluginComponent] = List(
      new Assembler(this)
    , new WrapFrontend(this)
    , new WrapEnricher(this)
    , new WrapBackend(this)
    , new VirtFrontend(this)
//    , new VirtBackend(this)
//    , new CheckExtensions(this)
    , new FinalComponent(this)
    //  , new Debug(this)
  )

  /** The description is printed with the option: -Xplugin-list */
  val description: String = "Optimization through staging"

  /** Plugin-specific options without -P:scalanizer:  */
  override def init(options: List[String], error: String => Unit): Boolean = {
    var enabled = true
    options foreach {
      case "save" => scalanizer.snConfig.withSave(true)
      case "read" => scalanizer.snConfig.withRead(true)
      case "debug" => scalanizer.snConfig.withDebug(true)
      case "disabled" =>
        enabled = false
      case option => error("Option not understood: " + option)
    }
    enabled
  }

  /** A description of the plugin's options */
  override val optionsHelp = Some(
    "  -P:"+ name +":save     Save META boilerplate and virtualized code to a file.\n"+
    "  -P:"+ name +":read     Read META boilerplate and virtualized code from a file.\n"+
    "  -P:"+ name +":debug    Print debug information: final AST and etc.\n"
  )
}

object ScalanizerPlugin {
  /** Yields the list of Components to be executed in this plugin */
  def components(global: Global) = {
    val plugin = new ScalanizerPlugin(global)
    plugin.components
  }
}

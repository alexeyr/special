package scalan.plugin

import java.lang.annotation.Annotation

import scalan.{FunctorType, ContainerType}
import scalan.meta.MetaConfig
import scalan.meta.ScalanAst.{WrapperConfig, NonWrapper}
import scalan.meta.scalanizer.ScalanizerConfig

class ScalanizerPluginConfig extends ScalanizerConfig {
  val targetModuleFolder = "library"

  /** The flag indicates that the plugin has to generate additional information and to store it
    * the debug folder and outputs to the console. */
  var debug: Boolean          = true
  def withDebug(d: Boolean): ScalanizerConfig = { debug = d; this }

  private def unitConfigTemplate(name: String, entityFile: String) =
    MetaConfig(
      name = name, srcPath = "", resourcePath = "", entityFile = entityFile,
      baseContextTrait = "scalan.Scalan", // used like this: trait ${module.name}Defs extends ${config.baseContextTrait.opt(t => s"$t with ")}${module.name} {
      extraImports = List(
        "scala.reflect.runtime.universe._",
        "scala.reflect._"
      ),
      isVirtualized = false, isStdEnabled = false
    )

  private def apiUnit(name: String, entityFile: String) =
    unitConfigTemplate(name, entityFile).copy(
      srcPath = "library-api/src/main/scala",
      resourcePath = "library-api/src/main/resources"
    )

  private def implUnit(name: String, entityFile: String) =
    unitConfigTemplate(name, entityFile).copy(
      srcPath = "library-impl/src/main/scala",
      resourcePath = "library-impl/src/main/resources"
    )

  /** A list of units that should be virtualized by scalan-meta. */
  val unitConfigs = List(
    apiUnit("Cols.scala", "scalan/collection/Cols.scala"),
    implUnit("ColsOverArrays.scala", "scalan/collection/ColsOverArrays.scala")
  )
  def getUnitConfig(unitName: String) = unitConfigs.find(_.name == unitName).getOrElse{
    sys.error(s"Cannot fing UnitConfig for '$unitName'")
  }

  val wrappersMetaConfig = MetaConfig(
    name = "Wrappers Config",
    srcPath = "library-api/src/main/scala",
    resourcePath = "library-api/src/main/resources",
    entityFile = "<shouldn't be used>",    // NOTE: there is no any wrapper source files
    extraImports = List(
      "scala.reflect.runtime.universe._",
      "scala.reflect._"
    ),
    isVirtualized = false, isStdEnabled = false
  )

  val wrapperConfigs: Map[String, WrapperConfig] = List(
    WrapperConfig(
      name = "Array",
      annotations = List(classOf[ContainerType], classOf[FunctorType]).map(_.getSimpleName)
    )
  ).map(w => (w.name, w)).toMap

  val nonWrappers: Map[String, NonWrapper] = List[NonWrapper](
    NonWrapper(name = "Predef"),
    NonWrapper(name = "<byname>"),
    NonWrapper(name = "ArrayOps"),
    NonWrapper(name = "WrappedArray"),
    NonWrapper(name = "CanBuildFrom")
  ).map(w => (w.name, w)).toMap
}

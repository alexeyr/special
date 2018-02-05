package scalan.meta

import scala.tools.nsc.Global
import scalan.util.FileUtil
import java.io.File

import com.trueaccord.lenses.{Mutation, Lens}

import scala.collection.mutable.{Map => MMap}
import scalan.meta.ScalanAst.{STraitCall, STpeDef, SUnitDef, STpeExpr, STpeFunc, SEntityAnnotation, createSubst, Entity, SConst, ExternalAnnotation, WrapperDescr, SEntityDef, Module}

class AstContext(val configs: List[UnitConfig], val parsers: ScalanParsers[Global], okLoadModules: Boolean = false)
    extends Symbols {

  /** Mapping of external type names to their wrappers. */
  private val wrappers = MMap[String, WrapperDescr]()

  /** Mapping of W-entities to the corresponding wrapped type name ("WArray" -> "Array") */
  private[scalan] val entityToWrapper = MMap[String, String]()

  /** Mapping of <packageName>.<moduleName> to definition.
    * Initial set of modules in loaded from configs and later new modules can be added. */
  private[scalan] val units = MMap[String, SUnitDef]()

  def loadUnitsFromResources(): Unit = {
    for (c <- configs) {
      val m = parsers.loadUnitDefFromResource(c.entityResource)
      addUnit(m)
    }
  }

  def loadModulesFromFolders(): Unit = {
    for (c <- configs) {
      val file = c.getFile
      try {
        val m = parsers.parseUnitFile(file)(new parsers.ParseCtx(c.isVirtualized)(this))
        addUnit(m)
      } catch {
        case t: Throwable =>
          val fullName = new File(FileUtil.currentWorkingDir, file.getPath)
          throw new RuntimeException(s"Error loading module from $fullName", t)
      }
    }
  }

  def updateWrapper(typeName: String, descr: WrapperDescr) = {
    wrappers(typeName) = descr
    val entityName = descr.module.traits(0).name
    entityToWrapper(entityName) = typeName
  }

  def externalTypes = wrappers.keySet

  def hasWrapper(typeName: String) = wrappers.contains(typeName)
  def getWrapper(typeName: String) = wrappers.get(typeName)

  def forEachWrapper(action: ((String, WrapperDescr)) => Unit) = {
    wrappers.foreach(action)
  }

  def transformWrappers(transformer: ((String, WrapperDescr)) => WrapperDescr) = {
    wrappers.transform(scala.Function.untupled(transformer))
  }

  /** The types that shouldn't be Rep[].
    * For example List("Elem", "Cont", "ClassTag") */
  val typeClasses = Set("Elem", "Cont", "ClassTag", "Functor")

  def isEntity(name: String): Boolean = {
    val res = for ( m <- units.values; e <- m.traits if e.name == name) yield ()
    res.nonEmpty
  }
  def isEntityCompanion(name: String): Boolean = {
    val res = for ( m <- units.values; e <- m.traits; c <- e.companion if c.name == name) yield ()
    res.nonEmpty
  }
  def isClass(name: String): Boolean = {
    val res = for ( m <- units.values; c <- m.classes if c.name == name) yield ()
    res.nonEmpty
  }
  def isClassCompanion(name: String): Boolean = {
    val res = for ( m <- units.values; c <- m.classes; comp <- c.companion if comp.name == name) yield ()
    res.nonEmpty
  }
  def isModule(name: String): Boolean = {
    units.valuesIterator.map(_.name).toSet.contains(name)
  }

  private[this] val highOrderTpes = Set("Thunk")

  def getKind(name: String): Int = {
    if (highOrderTpes.contains(name)) 1
    else {
      findModuleEntity(name).map { case (m, e) => e.tpeArgs.length }.getOrElse(0)
    }
  }

  def allModules: Iterator[SUnitDef] = wrappers.valuesIterator.map(_.module) ++ units.valuesIterator

  //TODO refactor to use Name for more precise ModuleEntity search
  def findModuleEntity(entityName: String): Option[(Module, Entity)] = {
    def isEqualName(m: SUnitDef, shortName: String, fullName: String): Boolean =
      fullName == shortName || fullName == s"${m.packageName}.$entityName.$shortName"

    def findByName(m: SUnitDef, es: List[SEntityDef]) =
      es.find(e => isEqualName(m, e.name, entityName))

    val res = allModules collectFirst scala.Function.unlift { m =>
      findByName(m, m.traits)
          .orElse(findByName(m, m.classes))
          .map((m, _))
    }
    res
  }

  def typeDefs: Map[String, STpeDef] = {
    val defs = for {
      m <- allModules
      t <- m.typeDefs
    }
      yield t.name -> t
    defs.toMap
  }

  def hasUnit(packageName: String, unitName: String): Boolean = {
    val key = SName.fullNameString(packageName, unitName)
    units.contains(key)
  }

  def getUnit(packageName: String, moduleName: String): SUnitDef = {
    val key = SName.fullNameString(packageName, moduleName)
    units(key)
  }
  def getUnit(name: SName): SUnitDef = getUnit(name.packageName, name.name)

  def addUnit(unit: SUnitDef): SUnitDef = {
    val key = unit.getUnitKey
    units(key) = unit
    unit
  }

  def removeUnit(key: String) = {
    units.remove(key)
  }

  def updateUnit(name: SName, by: Lens[SUnitDef, SUnitDef] => Mutation[SUnitDef]): SUnitDef = {
    val u = getUnit(name)
    val key = name.mkFullName
    val newUnit = u.update(by)
    units(key) = newUnit
    newUnit
  }

  object TypeDef {
    /** Recognizes usage of STpeDef and substitutes args to rhs */
    def unapply(tpe: STpeExpr): Option[(STpeDef, STpeExpr)] = tpe match {
      case STraitCall(n, args) =>
        typeDefs.get(n).map { td =>
          val subst = createSubst(td.tpeArgs, args)
          (td, td.tpe.applySubst(subst))
        }
      case _ => None
    }
  }

  object RepTypeOf {
    def unapply(tpe: STpeExpr): Option[STpeExpr] = tpe match {
      case STraitCall("Rep", Seq(t)) =>   // Rep[t] --> t
        Some(t)
      case STraitCall("RFunc", Seq(a, b)) =>  // RFunc[a,b] --> a => b
        Some(STpeFunc(a, b))
      case TypeDef(td, RepTypeOf(t)) => // type RepCol[args] = Rep[Col[args]] then RepCol[args] --> Col[args]
        Some(t)
      case _ => None
    }
  }

  object Entity {
    def unapply(name: String): Option[(Module, Entity)] =
      findModuleEntity(name)
  }

  object WrapperEntity {
    def unapply(name: String): Option[(SEntityDef, String)] = name match {
      case Entity(_, e) =>
        e.getAnnotation(ExternalAnnotation) match {
          case Some(SEntityAnnotation(_, List(SConst(externalName: String, _)))) => Some((e, externalName))
          case _ => None
        }
      case _ => None
    }
  }

}

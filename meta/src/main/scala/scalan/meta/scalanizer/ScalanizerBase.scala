package scalan.meta.scalanizer

import java.io.File
import scala.tools.nsc.Global
import scalan.meta.ScalanAst._
import scalan.meta.ScalanAstTransformers.{AstReplacer, TypeReplacer}
import scalan.meta._
import scalan.util.FileUtil

trait ScalanizerBase[+G <: Global]
  extends ScalanParsers[G] with ScalanGens[G] {
  import global._
  import context._

  def snState: ScalanizerState[G]

  def snConfig: ScalanizerConfig

//  def entityManagment: EntityManagement[G]

  def isNonWrapper(name: String): Boolean = {
    snConfig.nonWrappers.contains(name)
  }

  def isWrapper(name: String): Boolean = {
    val ok = !Set(
      isPrimitive _, isStandardType _,
      isEntity _, isEntityCompanion _,
      isClass _, isClassCompanion _,
      isModule _, isNonWrapper _
    ).exists(_ (name))
    ok
  }

  def getParents(externalType: Type) = {
    externalType.typeSymbol.typeSignature match {
      case PolyType(_, ClassInfoType(parents, _, _)) => parents
      case ClassInfoType(parents, _, _) => parents
      case _ => Nil
    }
  }

  def saveCode(module: SourceModuleConf, packageName: String, fileName: String, code: String): Unit = {
    saveCode(module.getResourceHome, packageName, fileName, code)
  }

  def saveWrapperCode(module: SourceModuleConf, packageName: String, fileName: String, code: String) = {
    saveCode(module.getResourceHome + "/wrappers", packageName, fileName, code)
  }

  def saveCode(sourceRoot: String, packageName: String, unitName: String, code: String): File = {
    val packagePath = packageName.replace('.', '/')
    val file = FileUtil.file(sourceRoot, packagePath, unitName + ".scala")
    file.mkdirs()
    FileUtil.write(file, code)
    file
  }

  def saveDebugCode(fileName: String, code: String) = {
    val folder = new File("library")  // this is the root of 'library' module
    val file = FileUtil.file(folder, "debug", fileName)
    file.mkdirs()
    FileUtil.write(file, code)
  }

  def saveImplCode(file: File, implCode: String) = {
    val fileName = file.getName.split('.')(0)
    val folder = file.getParentFile
    val implFile = FileUtil.file(folder, "impl", s"${fileName}Impl.scala")
    implFile.mkdirs()
    FileUtil.write(implFile, implCode)
  }
}

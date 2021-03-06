package scalan.compilation

import scalan.meta.SName

case class CodegenConfig(
      basePath: String,/** location to put generated files relative to the current project folder */
      moduleNames: Seq[SName]/** initial modules that should be generated, the rest are generated by dependency */
  ) {
}

object CodegenConfig {
//  def from(basePath: String, metaConfig: MetaConfig) = {
//    CodegenConfig(basePath)
//  }
}
package de.frosner.gitlabtemplate

import com.typesafe.config.Config

case class FileSystemSettings(allowEmpty: Boolean, dryRun: Boolean) {

  val flatToString: Seq[(String, String)] = Seq(
    ("allowEmpty", allowEmpty.toString),
    ("dryRun", dryRun.toString)
  )

}

object FileSystemSettings {

  def fromConfig(config: Config): FileSystemSettings = FileSystemSettings(
    allowEmpty = config.getBoolean("createEmptyKeyFile"),
    dryRun = config.getBoolean("dryRun")
  )

}

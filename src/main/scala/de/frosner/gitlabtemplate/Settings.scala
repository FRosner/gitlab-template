package de.frosner.gitlabtemplate

import com.typesafe.config.Config

case class Settings(gitlab: GitlabSettings, fileSystem: FileSystemSettings) {

  val flatToString: Seq[(String, String)] = {
    gitlab.flatToString ++ fileSystem.flatToString
  }

}

object Settings {

  def fromConfig(config: Config): Settings = Settings(
    gitlab = GitlabSettings.fromConfig(config.getConfig("source.gitlab")),
    fileSystem = FileSystemSettings.fromConfig(config.getConfig("sink.filesystem"))
  )

}

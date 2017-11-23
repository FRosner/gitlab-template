package de.frosner.gitlabtemplate

import java.nio.file.Path

import scala.concurrent.duration.FiniteDuration

case class GitlabTemplateConfig(source: SourceConfig,
                                sink: SinkConfig,
                                dryRun: Boolean,
                                renderFrequency: FiniteDuration)

case class SourceConfig(gitlab: GitlabConfig, technicalUsersKeys: TechnicalUsersKeysConfig)

case class TechnicalUsersKeysConfig(url: String)

case class SinkConfig(filesystem: FilesystemConfig)

case class GitlabConfig(onlyActiveUsers: Boolean, privateToken: String, url: String, timeout: FiniteDuration)

case class FilesystemConfig(path: Path, publicKeysFile: String, createEmptyKeyFile: Boolean)

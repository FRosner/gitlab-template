/*
 * Copyright 2017 Frank Rosner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

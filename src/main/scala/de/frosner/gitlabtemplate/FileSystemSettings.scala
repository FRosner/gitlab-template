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

import java.nio.file.{Path, Paths}

import com.typesafe.config.Config

case class FileSystemSettings(path: Path, publicKeysFile: String, allowEmpty: Boolean) {

  val flatToString: Seq[(String, String)] = Seq(
    ("allowEmpty", allowEmpty.toString),
    ("path", s"$path (${path.toAbsolutePath})"),
    ("publicKeysFile", publicKeysFile)
  )

}

object FileSystemSettings {

  def fromConfig(config: Config): FileSystemSettings = FileSystemSettings(
    allowEmpty = config.getBoolean("createEmptyKeyFile"),
    path = Paths.get(config.getString("path")),
    publicKeysFile = config.getString("publicKeysFile")
  )

}

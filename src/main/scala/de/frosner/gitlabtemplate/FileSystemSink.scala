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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.StrictLogging

import scala.util.Try

class FileSystemSink(rootDirectory: Path, publicKeysFileName: String, allowEmpty: Boolean) extends StrictLogging {

  def write(usersAndKeys: Seq[(User, Seq[PublicKey])]): Try[(Int, Int)] = synchronized {
    Try {
      val numKeysWritten = usersAndKeys.map {
        case (user, keys) =>
          val userDir = rootDirectory.resolve(user.username)
          val publicKeysFile = userDir.resolve(publicKeysFileName)
          logger.debug(s"Creating user directory '${userDir.toAbsolutePath}'")
          Files.createDirectories(userDir)
          if (keys.nonEmpty || allowEmpty) {
            logger.debug(s"Writing public keys to '${publicKeysFile.toAbsolutePath}'")
            Files.write(publicKeysFile, keys.map(_.key).mkString("\n").getBytes(StandardCharsets.UTF_8))
          } else {
            logger.debug(s"Skipping '${publicKeysFile.toAbsolutePath}' because it would be empty")
          }
          keys.size
      }.sum
      (usersAndKeys.size, numKeysWritten)
    }
  }

}

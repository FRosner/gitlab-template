package de.frosner.gitlabtemplate

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}

import scala.util.Try

class FileSystemSink(rootDirectory: Path, publicKeysFileName: String, allowEmpty: Boolean) extends StrictLogging {

  def write(usersAndKeys: Map[Username, Set[PublicKeyType]]): Try[(Int, Int)] = synchronized {
    Try {
      val numKeysWritten = usersAndKeys.map {
        case (user, keys) =>
          if (keys.nonEmpty || allowEmpty) {
            val userDir = rootDirectory.resolve(user)
            val publicKeysFile = userDir.resolve(publicKeysFileName)
            if (userDir.toFile.isDirectory) {
              logger.debug(s"User directory '${userDir.toAbsolutePath}' already exists")
            } else {
              logger.debug(s"Creating user directory '${userDir.toAbsolutePath}'")
            }
            Files.createDirectories(userDir)
            logger.debug(s"Writing public keys to '${publicKeysFile.toAbsolutePath}'")
            Files.write(publicKeysFile, keys.mkString("\n").getBytes(StandardCharsets.UTF_8))
          } else {
            logger.debug(s"Skipping '$user' because it doesn't have any keys")
          }
          keys.size
      }.sum
      (usersAndKeys.size, numKeysWritten)
    }
  }

}

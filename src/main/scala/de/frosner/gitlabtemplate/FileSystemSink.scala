package de.frosner.gitlabtemplate

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}

import scala.util.Try

class FileSystemSink(rootDirectory: Path, publicKeysFileName: String, allowEmpty: Boolean) extends StrictLogging {

  def write(usersAndKeys: Map[Username, Set[PublicKeyType]]): Try[(Int, Int)] = synchronized {
    Try {
      val presentUserDirectories = rootDirectory.toFile.listFiles().filter(_.isDirectory).map(_.getName).toSet
      val nonEmptyUsersAndKeys = usersAndKeys.filter {
        case (user, keys) => keys.nonEmpty || allowEmpty
      }
      val usersToDelete = presentUserDirectories.diff(nonEmptyUsersAndKeys.keySet)
      usersToDelete.foreach { user =>
        val userDir = rootDirectory.resolve(user)
        val userDirPath = userDir.toAbsolutePath
        logger.debug(s"Deleting user directory $userDirPath because there are no keys for this user")
        deleteRecursively(userDir.toFile)
      }
      val numKeysWritten = nonEmptyUsersAndKeys.map {
        case (user, keys) =>
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
          keys.size
      }.sum
      (nonEmptyUsersAndKeys.size, numKeysWritten)
    }
  }

  private def deleteRecursively(file: File): Unit =
    Try {
      if (file.isDirectory)
        file.listFiles.foreach(deleteRecursively)
      if (file.exists && !file.delete)
        throw new Exception(s"Unable to delete ${file.getAbsolutePath}")
    }

}

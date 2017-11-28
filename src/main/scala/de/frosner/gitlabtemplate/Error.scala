package de.frosner.gitlabtemplate

import play.api.libs.json.{JsPath, JsonValidationError}
import pureconfig.error.ConfigReaderFailures

sealed trait Error {
  val message: String
  def toException: Exception = new GitlabTemplateException(message)
}

object Error {

  final case class GitlabError(error: Seq[(JsPath, Seq[JsonValidationError])]) extends Error {
    val message = s"Could not parse Gitlab response: $error"
  }

  final case class TechnicalUsersKeysError(configReader: ConfigReaderFailures) extends Error {
    val message = s"Could not parse technical user key file: $configReader"
  }

  final case class FilesystemError(throwable: Throwable) extends Error {
    val message = s"Could not persist keys on the file system: $throwable"
  }

}

class GitlabTemplateException(message: String) extends Exception(message)

package de.frosner.gitlabtemplate

import play.api.libs.json.{JsPath, JsonValidationError}
import pureconfig.error.ConfigReaderFailures

sealed trait Error

object Error {

  final case class GitlabError(error: Seq[(JsPath, Seq[JsonValidationError])]) extends Error

  final case class TechnicalUsersKeysError(configReader: ConfigReaderFailures) extends Error

}

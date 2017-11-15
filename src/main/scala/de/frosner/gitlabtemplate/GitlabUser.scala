package de.frosner.gitlabtemplate

import de.frosner.gitlabtemplate.Main.Username
import play.api.libs.json.{Format, Json}

final case class GitlabUser(id: Long, username: Username)

object GitlabUser {

  implicit val format: Format[GitlabUser] = Json.format[GitlabUser]

}

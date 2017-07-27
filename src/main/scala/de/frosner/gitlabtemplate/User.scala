package de.frosner.gitlabtemplate

import play.api.libs.json.{Format, Json, Reads}

final case class User(id: Long, username: String)

object User {

  implicit val format: Format[User] = Json.format[User]

}

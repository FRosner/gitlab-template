package de.frosner.gitlabtemplate

import play.api.libs.json.{Format, Json}

final case class PublicKey(key: String)

object PublicKey {

  implicit val format: Format[PublicKey] = Json.format[PublicKey]

}

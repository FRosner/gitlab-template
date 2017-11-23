package de.frosner.gitlabtemplate

import de.frosner.gitlabtemplate.Main.PublicKeyType
import play.api.libs.json.{Format, Json}

final case class PublicKey(key: PublicKeyType)

object PublicKey {

  implicit val format: Format[PublicKey] = Json.format[PublicKey]

}

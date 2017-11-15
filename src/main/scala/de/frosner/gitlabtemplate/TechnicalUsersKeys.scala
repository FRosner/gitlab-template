package de.frosner.gitlabtemplate

import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}
import de.frosner.gitlabtemplate.TechnicalUsersKeys.TechnicalUser

case class TechnicalUsersKeys(authorizedUsers: Map[TechnicalUser, Set[Username]],
                              authorizedKeys: Map[Username, Set[PublicKeyType]])

object TechnicalUsersKeys {
  type TechnicalUser = String
}

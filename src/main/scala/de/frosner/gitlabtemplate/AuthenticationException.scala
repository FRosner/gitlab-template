package de.frosner.gitlabtemplate

case class AuthenticationException(username: String, url: String)
    extends Exception(s"User '$username' failed to authenticate at '$url'")

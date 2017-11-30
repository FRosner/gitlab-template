package de.frosner.gitlabtemplate

case class UnexpectedStatusCodeException(statusCode: Int, url: String)
    extends Exception(s"Unexpected status code '$statusCode' from '$url'")

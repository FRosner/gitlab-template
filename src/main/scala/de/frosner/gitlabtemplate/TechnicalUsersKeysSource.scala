package de.frosner.gitlabtemplate

import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Error.TechnicalUsersKeysError
import pureconfig._
import cats.instances.all._
import cats.syntax.all._
import play.api.libs.ws.{StandaloneWSClient, WSAuthScheme}
import pureconfig.error.ConfigReaderFailures

import scala.concurrent.{ExecutionContext, Future}

class TechnicalUsersKeysSource(wsClient: StandaloneWSClient, url: String, httpBasicAuthConfig: HttpBasicAuthConfig)(
    implicit ec: ExecutionContext)
    extends StrictLogging {

  def get: EitherT[Future, Error, TechnicalUsersKeys] = {
    val request = wsClient
      .url(url)
    val authRequest =
      if (httpBasicAuthConfig.enabled)
        request.withAuth(httpBasicAuthConfig.username, httpBasicAuthConfig.password, WSAuthScheme.BASIC)
      else
        request
    logger.debug(s"Requesting technical users keys: ${request.url}")
    EitherT(authRequest.get().map { response =>
      response.status match {
        case 200   => loadConfig[TechnicalUsersKeys](ConfigFactory.parseString(response.body))
        case 401   => throw AuthenticationException(httpBasicAuthConfig.username, url)
        case other => throw UnexpectedStatusCodeException(other, url)
      }
    }).leftMap(TechnicalUsersKeysError)
  }

}

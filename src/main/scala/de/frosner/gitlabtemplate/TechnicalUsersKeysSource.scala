package de.frosner.gitlabtemplate

import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Error.TechnicalUsersKeysError
import pureconfig._
import cats.instances.all._
import cats.syntax.all._
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.{ExecutionContext, Future}

class TechnicalUsersKeysSource(wsClient: StandaloneWSClient, url: String)(implicit ec: ExecutionContext)
    extends StrictLogging {

  def get: EitherT[Future, Error, TechnicalUsersKeys] = {
    val request = wsClient
      .url(url)
    logger.debug(s"Requesting technical users keys: ${request.url}")
    EitherT(request.get().map { response =>
      loadConfig[TechnicalUsersKeys](ConfigFactory.parseString(response.body))
    }).leftMap(TechnicalUsersKeysError)
  }

}

/*
 * Copyright 2017 Frank Rosner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

final class TechnicalUsersKeysSource(wsClient: StandaloneWSClient, url: String)(implicit ec: ExecutionContext)
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

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
import play.api.libs.json.{JsPath, JsValue, Json, JsonValidationError}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.StandaloneWSClient
import cats.instances.all._
import cats.syntax.all._
import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Error.GitlabError
import GitlabUser.format
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}

import scala.concurrent.{ExecutionContext, Future}

final class GitlabSource(wsClient: StandaloneWSClient, url: String, privateToken: String)(implicit ec: ExecutionContext)
    extends StrictLogging {

  def getUsers(requireActive: Boolean): EitherT[Future, Error, Set[GitlabUser]] = {
    val activeFilter = if (requireActive) "?active=true" else ""
    val request = wsClient
      .url(s"$url/api/v4/users$activeFilter")
      .withHttpHeaders(("PRIVATE-TOKEN", privateToken))
    logger.debug(s"Requesting users: ${request.url}")
    EitherT(request.get().map { response =>
      val body = response.body[JsValue]
      Json.fromJson[Set[GitlabUser]](body).asEither
    }).leftMap(GitlabError)
  }

  def getSshKeys(users: Set[GitlabUser]): EitherT[Future, Error, Map[Username, Set[PublicKeyType]]] = {
    val futureUsersAndKeys =
      Future.sequence {
        users.map { user =>
          val request = wsClient
            .url(s"$url/api/v4/users/${user.id}/keys")
            .withHttpHeaders(("PRIVATE-TOKEN", privateToken))
          logger.debug(s"Requesting public keys for ${user.username}: ${request.url}")
          request.get().map { response =>
            Json
              .fromJson[Set[PublicKey]](response.body[JsValue])
              .asEither
              .map(keys => (user.username, keys.map(_.key)))
          }
        }.toList
      }
    EitherT(futureUsersAndKeys.map(_.sequenceU)).map(_.toMap).leftMap(GitlabError)
  }

}

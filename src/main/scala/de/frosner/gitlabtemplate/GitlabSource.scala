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

class GitlabSource(wsClient: StandaloneWSClient,
                   url: String,
                   privateToken: String,
                   onlyActiveUsers: Boolean,
                   perPage: Int)(implicit ec: ExecutionContext)
    extends StrictLogging {

  def getUsers: EitherT[Future, Error, Set[GitlabUser]] = {
    val activeFilter = if (onlyActiveUsers) "&active=true" else ""
    getUsers(EitherT.right[Future, Error, Set[GitlabUser]](Future.successful(Set())), activeFilter, 1, 0)
  }

  private def getUsers(result: EitherT[Future, Error, Set[GitlabUser]],
                       activeFilter: String,
                       pageNumber: Int,
                       prevSize: Int): EitherT[Future, Error, Set[GitlabUser]] =
    result.flatMap(
      gitlabUsers => {
        val currentSize = gitlabUsers.size
        if (pageNumber > 1 && prevSize == currentSize) {
          // If it is not the first page the results are the same return
          EitherT.right(Future.successful(gitlabUsers))
        } else { // else recurse
          val request = wsClient
            .url(s"$url/api/v4/users?per_page=$perPage&page=$pageNumber$activeFilter")
            .withHttpHeaders(("PRIVATE-TOKEN", privateToken))
          logger.debug(s"Requesting users: ${request.url}")
          EitherT(request.get().map { response =>
            val body = response.body[JsValue]
            Json.fromJson[Set[GitlabUser]](body).asEither
          }).leftMap(GitlabError)
            .leftMap(_.asInstanceOf[Error])
            .flatMap(
              newGitlabUsers => {
                // FIXME: This might be bad for performance if there are too many pages
                // Can we make this tail recursive?
                getUsers(
                  EitherT.right(Future.successful(gitlabUsers ++ newGitlabUsers)),
                  activeFilter,
                  pageNumber + 1,
                  currentSize
                )
              }
            )
        }
      }
    )

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

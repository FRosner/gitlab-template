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

import java.io
import java.util.concurrent._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import play.api.libs.json.{JsPath, JsonValidationError}
import play.api.libs.ws.ahc._
import pureconfig._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{Scheduler, UncaughtExceptionReporter}
import cats.data._
import cats.implicits._
import cats.kernel.Semigroup
import de.frosner.gitlabtemplate.Error.GitlabError
import de.frosner.gitlabtemplate.TechnicalUsersKeys.TechnicalUser

import scala.collection.generic.GenericTraversableTemplate
import scala.collection.{GenIterable, GenIterableLike, GenTraversable, IterableLike}

object Main extends StrictLogging {

  type PublicKeyType = String
  type Username = String

  def main(args: Array[String]): Unit = {
    val conf = loadConfigOrThrow[GitlabTemplateConfig](ConfigFactory.load().getConfig("gitlab-template"))
    val gitlabConf = conf.source.gitlab
    val filesystemConf = conf.sink.filesystem

    implicit val system = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }
    implicit val materializer = ActorMaterializer()

    val wsClient = StandaloneAhcWSClient()

    sys.addShutdownHook {
      wsClient.close()
      system.terminate()
    }

    val gitlabClient =
      new GitlabSource(wsClient, gitlabConf.url, gitlabConf.privateToken)

    val technicalUsersKeysSource = new TechnicalUsersKeysSource(wsClient, conf.source.technicalUsersKeys.url)

    val filesystemSink =
      new FileSystemSink(filesystemConf.path, filesystemConf.publicKeysFile, filesystemConf.createEmptyKeyFile)

    val executorService = scala.concurrent.ExecutionContext.Implicits.global
    val scheduler =
      Scheduler(
        Executors.newSingleThreadScheduledExecutor(),
        executorService,
        UncaughtExceptionReporter(executorService.reportFailure),
        AlwaysAsyncExecution
      )
    scheduler.scheduleWithFixedDelay(0.seconds, conf.renderFrequency) {
      Try {
        // TODO allow to disable/enable both sources individually
        val allSshKeys = for {
          users <- gitlabClient.getUsers(conf.source.gitlab.onlyActiveUsers)
          userSshKeys <- gitlabClient.getSshKeys(users)
          filteredUserSshKeys <- filterEmptyKeysAndActiveUsers(userSshKeys, filesystemConf.createEmptyKeyFile)
          technicalUserKeys <- technicalUsersKeysSource.get
          mergedKeys <- mergeTechnicalWithGitlabUsers(filteredUserSshKeys, technicalUserKeys)
        } yield mergedKeys

        allSshKeys.value.onComplete {
          // TODO map instead of onComplete to avoid getting killed and also be more testable
          case Failure(error) =>
            logger.error(s"Extracting keys failed: ${error.getMessage}")
          case Success(Left(error)) =>
            throw new Exception(s"Cannot parse Gitlab response: $error")
          case Success(Right(usersAndKeys)) =>
            if (conf.dryRun) {
              usersAndKeys.foreach {
                case (user, keys) =>
                  keys.foreach(key => logger.info(s"$user\t $key"))
              }
            } else {
              filesystemSink.write(usersAndKeys) match {
                case Success((numUsers, numKeys)) =>
                  logger.debug(s"Successfully persisted a total of $numKeys key(s) for $numUsers user(s)")
                case Failure(exception) => throw exception
              }
            }
        }
        Await.ready(allSshKeys.value, gitlabConf.timeout)
      }.recover {
        case throwable =>
          throwable.printStackTrace()
          System.exit(1)
          throw throwable
      }
    }
  }

  def filterEmptyKeysAndActiveUsers(
      usersAndKeys: Map[Username, Set[PublicKeyType]],
      onlyActiveUsers: Boolean): EitherT[Future, Error, Map[Username, Set[PublicKeyType]]] = {
    val filteredKeys = usersAndKeys.filter {
      case (user, keys) => keys.nonEmpty || onlyActiveUsers
    }
    EitherT.pure(filteredKeys)
  }

  def mergeTechnicalWithGitlabUsers(
      gitlabKeys: Map[Username, Set[PublicKeyType]],
      technicalUsersAndKeys: TechnicalUsersKeys): EitherT[Future, Error, Map[TechnicalUser, Set[PublicKeyType]]] = {
    val authorizedUsersKeys = technicalUsersAndKeys.authorizedUsers
      .map {
        case (technicalUser, authorizedUsers) =>
          (technicalUser, authorizedUsers.flatMap { username =>
            val userKeys = gitlabKeys.get(username)
            if (userKeys.isEmpty) {
              logger.warn(
                s"User '$username' is supposed to have its authorized keys put to technical user " +
                  s"'$technicalUser' but '$username' does not exist on Gitlab.")
            }
            userKeys
          }.flatten)
      }
    val technicalUserKeys = Semigroup.combine(authorizedUsersKeys, technicalUsersAndKeys.authorizedKeys)
    val allKeys = Semigroup.combine(technicalUserKeys, gitlabKeys)
    EitherT.pure(allKeys)
  }

}

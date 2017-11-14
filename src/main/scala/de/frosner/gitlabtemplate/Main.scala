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

import java.util.concurrent._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import play.api.libs.json.{JsPath, JsonValidationError}
import play.api.libs.ws.ahc._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Main extends StrictLogging {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    config.checkValid(ConfigFactory.defaultReference())
    val settings = Settings.fromConfig(config.getConfig("gitlabtemplate"))
    settings.flatToString.foreach {
      case (key, value) => logger.info(s"Using configuration: $key='$value'")
    }

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
      new GitlabSource(wsClient, settings.gitlab.url, settings.gitlab.privateToken)

    val filesystemSink =
      new FileSystemSink(settings.fileSystem.path, settings.fileSystem.publicKeysFile, settings.fileSystem.allowEmpty)

    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new Runnable {
      def run(): Unit =
        Try {
          val futureUsers = gitlabClient.getUsers(settings.gitlab.requireActive)
          val futureSshKeys: Future[Either[Seq[(JsPath, Seq[JsonValidationError])], Seq[(User, Seq[PublicKey])]]] =
            futureUsers.flatMap {
              case Left(error)  => Future.successful(Left(error))
              case Right(users) => gitlabClient.getSshKeys(users)
            }
          val filteredFutureSshKeys = futureSshKeys.map {
            _.map {
              _.filter {
                case (user, keys) => keys.nonEmpty || settings.gitlab.requireActive
              }
            }
          }

          filteredFutureSshKeys.onComplete {
            case Failure(error) =>
              logger.error(s"Extracting keys failed: ${error.getMessage}")
            case Success(Left(error)) =>
              throw new Exception(s"Cannot parse Gitlab response: $error")
            case Success(Right(usersAndKeys)) =>
              if (settings.dryRun) {
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
          Await.ready(filteredFutureSshKeys, Duration(10, TimeUnit.SECONDS))
        }.recover {
          case throwable =>
            throwable.printStackTrace()
            System.exit(1)
            throw throwable
        }
    }
    val f = ex.scheduleAtFixedRate(task, 0, settings.gitlab.pollingFreqSec, TimeUnit.SECONDS)
  }

}

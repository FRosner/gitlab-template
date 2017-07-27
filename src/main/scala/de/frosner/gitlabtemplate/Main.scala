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
import com.typesafe.scalalogging.StrictLogging
import play.api.libs.json.{JsPath, JsonValidationError}
import play.api.libs.ws.ahc._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object Main extends StrictLogging {

  def main(args: Array[String]): Unit = {
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

    val allowEmpty = true // TODO configurable
    val requireActive = false // TODO configurable
    val pollingFreqSec = 2L // TODO configurable
    val gitlabUrl = "http://localhost" // TODO configurable
    val dryRun = false // TODO configurable

    val gitlabClient =
      new GitlabClient(wsClient, gitlabUrl, "GNvDFFr7SHzZf6Zte5Xq")

    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new Runnable {
      def run() = {
        val futureUsers = gitlabClient.getUsers(requireActive)
        val futureSshKeys: Future[Either[Seq[(JsPath, Seq[JsonValidationError])], Seq[(User, Seq[PublicKey])]]] =
          futureUsers.flatMap {
            case Left(error)  => Future.successful(Left(error))
            case Right(users) => gitlabClient.getSshKeys(users)
          }
        val filteredFutureSshKeys = futureSshKeys.map {
          _.map {
            _.filter {
              case (user, keys) => keys.nonEmpty || allowEmpty
            }
          }
        }
        filteredFutureSshKeys.onComplete {
          case Failure(error) =>
            logger.error(s"Extracting keys failed: ${error.getMessage}")
          case Success(Left(error)) =>
            logger.error(s"Cannot parse Gitlab response: $error")
            System.exit(1)
          case Success(Right(usersAndKeys)) =>
            usersAndKeys.foreach {
              case (user, keys) =>
                val keysFormatted = keys.map(k => s"'${k.key}'").mkString(", ")
                logger.debug(s"Successfully extracted the following keys for $user: $keysFormatted")
            }
        }
        Await.ready(filteredFutureSshKeys, Duration(10, TimeUnit.SECONDS))
      }
    }
    val f = ex.scheduleAtFixedRate(task, 0, pollingFreqSec, TimeUnit.SECONDS)
  }

}

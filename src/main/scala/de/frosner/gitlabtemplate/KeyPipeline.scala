package de.frosner.gitlabtemplate

import cats.data._
import cats.implicits._
import cats.kernel.Semigroup
import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class KeyPipeline(val gitlabSource: GitlabSource,
                  val technicalUsersKeysSource: TechnicalUsersKeysSource,
                  val fileSystemSink: FileSystemSink,
                  val dryRun: Boolean)(implicit ec: ExecutionContext)
    extends StrictLogging {

  // TODO allow to disable/enable both sources individually
  def generateKeys(timeout: FiniteDuration)(implicit ec: ExecutionContext): Try[Unit] =
    Try {
      val allSshKeys = for {
        users <- gitlabSource.getUsers
        userSshKeys <- gitlabSource.getSshKeys(users)
        technicalUserKeys <- technicalUsersKeysSource.get
        mergedKeys <- mergeTechnicalWithGitlabUsers(userSshKeys, technicalUserKeys)
      } yield mergedKeys

      val persistedKeys = allSshKeys
        .map { usersAndKeys =>
          if (dryRun) {
            usersAndKeys.foreach {
              case (user, keys) =>
                keys.foreach(key => logger.info(s"$user\t $key"))
            }
          } else {
            fileSystemSink.write(usersAndKeys) match {
              case Success((numUsers, numKeys)) =>
                logger.debug(s"Successfully persisted a total of $numKeys key(s) for $numUsers user(s)")
              case Failure(exception) => throw exception
            }
          }
        }
        .recover {
          case error => throw new Exception(s"Cannot parse Gitlab response: $error")
        }
      persistedKeys.value.failed.foreach { error =>
        logger.error(s"Extracting keys failed: ${error.getMessage}")
        throw error
      }
      Await.result(persistedKeys.value, timeout) // TODO check for failing future here
    }

  private def mergeTechnicalWithGitlabUsers(
      gitlabKeys: Map[Username, Set[PublicKeyType]],
      technicalUsersAndKeys: TechnicalUsersKeys): EitherT[Future, Error, Map[Username, Set[PublicKeyType]]] = {
    val authorizedUsersKeys = technicalUsersAndKeys.authorizedUsers
      .map {
        case (technicalUser, authorizedUsers) =>
          (technicalUser, authorizedUsers.flatMap { username =>
            val userKeys = gitlabKeys.get(username)
            if (userKeys.getOrElse(Set.empty).isEmpty) {
              logger.warn(
                s"User '$username' is supposed to have its authorized keys put to technical user " +
                  s"'$technicalUser' but did not provide any keys on Gitlab, is inactive, or doesn't exist.")
            }
            userKeys
          }.flatten)
      }
    val technicalUserKeys = Semigroup.combine(technicalUsersAndKeys.authorizedKeys, authorizedUsersKeys)
    val allKeys = Semigroup.combine(technicalUserKeys, gitlabKeys)
    EitherT.pure(allKeys)
  }

}

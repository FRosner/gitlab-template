package de.frosner.gitlabtemplate

import cats.data._
import cats.implicits._
import cats.kernel.Semigroup
import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Error.{FilesystemError, GitlabError}
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
      val result = for {
        users <- gitlabSource.getUsers
        userSshKeys <- gitlabSource.getSshKeys(users)
        technicalUserKeys <- technicalUsersKeysSource.get
        mergedKeys <- mergeTechnicalWithGitlabUsers(userSshKeys, technicalUserKeys)
        persistedKeys <- persistKeys(mergedKeys, dryRun)
      } yield persistedKeys

      Try(Await.result(result.value, timeout)) match {
        case Success(result) =>
          result match {
            case Left(error)         => throw error.toException
            case Right(usersAndKeys) => ()
          }
        case Failure(throwable) => throw new GitlabTemplateException(s"Failed to extract keys: $throwable")
      }
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

  private def persistKeys(mergedKeys: Map[Username, Set[PublicKeyType]],
                          dryRun: Boolean): EitherT[Future, Error, Map[Username, Set[PublicKeyType]]] =
    if (dryRun) {
      mergedKeys.foreach {
        case (user, keys) =>
          keys.foreach(key => logger.info(s"$user\t $key"))
      }
      EitherT.pure(mergedKeys)
    } else {
      EitherT
        .fromEither[Future](fileSystemSink.write(mergedKeys).toEither.map(_ => mergedKeys))
        .leftMap(FilesystemError)
    }

}

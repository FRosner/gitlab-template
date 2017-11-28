package de.frosner.gitlabtemplate

import cats.data.EitherT
import cats.data._
import cats.implicits._
import de.frosner.gitlabtemplate.Error.{GitlabError, TechnicalUsersKeysError}
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class KeyPipelineSpec extends FlatSpec with Matchers with MockitoSugar {

  private val testName = this.getClass.getSimpleName

  "A key pipeline" should "merge user and technical user keys correctly" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get).thenReturn(EitherT.pure[Future, Error, TechnicalUsersKeys](technicalUsersKeys))

    val fileSystemSink = mock[FileSystemSink]
    when(
      fileSystemSink.write(
        Map(
          "usr1" -> Set("key1", "key2"),
          "tusr1" -> Set("key1", "key2", "key3")
        ))).thenReturn(Success((2, 5)))

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    keyPipeline.generateKeys(5.seconds) shouldBe Success(())
  }

  it should "not persist the keys in a dry run" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get).thenReturn(EitherT.pure[Future, Error, TechnicalUsersKeys](technicalUsersKeys))

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, true)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe Success(())
  }

  it should "warn if an authorized user from a technical user does not exist" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map.empty[Username, Set[PublicKeyType]]
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get).thenReturn(EitherT.pure[Future, Error, TechnicalUsersKeys](technicalUsersKeys))

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    when(
      fileSystemSink.write(
        Map(
          "tusr1" -> Set("key3")
        ))).thenReturn(Success((1, 1)))
    val result = keyPipeline.generateKeys(5.seconds)
    result shouldBe Success(())
  }

  it should "warn if an authorized user from a technical user does not have any keys" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set.empty[PublicKeyType])
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get).thenReturn(EitherT.pure[Future, Error, TechnicalUsersKeys](technicalUsersKeys))

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    when(
      fileSystemSink.write(
        Map(
          "usr1" -> Set.empty[PublicKeyType],
          "tusr1" -> Set("key3")
        ))).thenReturn(Success((2, 1)))
    val result = keyPipeline.generateKeys(5.seconds)
    result shouldBe Success(())
  }

  it should "fail if getting users from the Gitlab source fails (Error)" in {
    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers)
      .thenReturn(EitherT.fromEither[Future](Left[Error, Set[GitlabUser]](GitlabError(Seq.empty))))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, true)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

  it should "fail if getting users from the Gitlab source fails (Exception)" in {
    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers)
      .thenReturn(EitherT(Future.failed[Either[Error, Set[GitlabUser]]](new Exception("gitlab not reachable"))))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, true)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

  it should "fail if getting the ssh keys from gitlab fails (Error)" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.fromEither[Future](Left[Error, Map[Username, Set[PublicKeyType]]](GitlabError(Seq.empty))))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

  it should "fail if getting the ssh keys from gitlab fails (Exception)" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(
        EitherT(Future.failed[Either[Error, Map[Username, Set[PublicKeyType]]]](new Exception("not reachable"))))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

  it should "fail if the authorized keys source fails (Error)" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get)
      .thenReturn(EitherT.fromEither[Future](Left[Error, TechnicalUsersKeys](TechnicalUsersKeysError(null))))

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

  it should "fail if the authorized keys source fails (Exception)" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get)
      .thenReturn(EitherT(Future.failed[Either[Error, TechnicalUsersKeys]](new Exception("not reachable"))))

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    val result = keyPipeline.generateKeys(5.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

  it should "fail if the file system sink fails" in {
    val users = Set(GitlabUser(1, "usr1"))
    val userKeys = Map("usr1" -> Set("key1", "key2"))
    val technicalUsersKeys = TechnicalUsersKeys(
      authorizedUsers = Map("tusr1" -> Set("usr1")),
      authorizedKeys = Map("tusr1" -> Set("key3"))
    )

    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers).thenReturn(EitherT.pure[Future, Error, Set[GitlabUser]](users))
    when(gitlabSource.getSshKeys(users))
      .thenReturn(EitherT.pure[Future, Error, Map[Username, Set[PublicKeyType]]](userKeys))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]
    when(technicalUsersKeysSource.get).thenReturn(EitherT.pure[Future, Error, TechnicalUsersKeys](technicalUsersKeys))

    val fileSystemSink = mock[FileSystemSink]
    when(
      fileSystemSink.write(
        Map(
          "usr1" -> Set("key1", "key2"),
          "tusr1" -> Set("key1", "key2", "key3")
        ))).thenReturn(Failure(new Exception("filesystem busy")))

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, false)
    keyPipeline.generateKeys(5.seconds) shouldBe a[Failure[_]]
  }

  it should "fail if the whole operation takes longer than the timeout" in {
    val gitlabSource = mock[GitlabSource]
    when(gitlabSource.getUsers)
      .thenReturn(EitherT(Future[Either[Error, Set[GitlabUser]]] {
        Thread.sleep(5000)
        Right(Set.empty)
      }))

    val technicalUsersKeysSource = mock[TechnicalUsersKeysSource]

    val fileSystemSink = mock[FileSystemSink]

    val keyPipeline = new KeyPipeline(gitlabSource, technicalUsersKeysSource, fileSystemSink, true)
    val result = keyPipeline.generateKeys(2.seconds)
    verifyZeroInteractions(fileSystemSink)
    result shouldBe a[Failure[_]]
  }

}

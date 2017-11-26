package de.frosner.gitlabtemplate

import cats.data.EitherT
import cats.data._
import cats.implicits._
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

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

}

package de.frosner.gitlabtemplate

import java.nio.file.Files

import org.scalatest._

import scala.io.Source
import scala.util.{Failure, Success}

class FileSystemSinkSpec extends FlatSpec with Matchers {

  private val testName = this.getClass.getSimpleName

  private val usersAndKeys = Map(
    "user1" -> Set("key1", "key2"),
    "user2" -> Set("key1"),
    "user3" -> Set.empty[String]
  )

  "A filesystem sink" should "return the number of keys and users written" in {
    val tmpDir = Files.createTempDirectory(testName)
    val sink = new FileSystemSink(tmpDir, "authorized_keys", true)
    sink.write(usersAndKeys) === Success(())
  }

  it should "return a failure if the keys cannot be written" in {
    val tmpDir = Files.createTempDirectory(testName)
    val sink = new FileSystemSink(tmpDir, "authorized_keys", true)
    tmpDir.toFile.delete()
    sink.write(usersAndKeys) shouldBe a[Failure[_]]
  }

  it should "create the folders for each users if they don't exist" in {
    val tmpDir = Files.createTempDirectory(testName)
    val sink = new FileSystemSink(tmpDir, "authorized_keys", true)
    sink.write(usersAndKeys)
    all(usersAndKeys.keys.map(user => tmpDir.resolve(user).toFile)) should exist
  }

  it should "not create the folders for users that have no keys if not desired" in {
    val tmpDir = Files.createTempDirectory(testName)
    val sink = new FileSystemSink(tmpDir, "authorized_keys", false)
    sink.write(usersAndKeys)
    tmpDir.resolve("user3").toFile shouldNot exist
  }

  it should "write the keys to the authorized keys file" in {
    val tmpDir = Files.createTempDirectory(testName)
    val authorizedKeysFileName = "authorized_keys"
    val sink = new FileSystemSink(tmpDir, authorizedKeysFileName, true)
    sink.write(usersAndKeys)
    val writtenUsersAndKeys = usersAndKeys.keys.map { user =>
      val file = tmpDir.resolve(user).resolve(authorizedKeysFileName).toFile
      (user, Source.fromFile(file).getLines().toSet)
    }.toMap
    writtenUsersAndKeys === usersAndKeys
  }

  it should "delete user folders that are longer present" in {
    val tmpDir = Files.createTempDirectory(testName)
    val sink = new FileSystemSink(tmpDir, "authorized_keys", true)
    sink.write(usersAndKeys)
    sink.write(usersAndKeys - "user1")
    tmpDir.toFile.list() shouldBe Array("user2", "user3")
  }

  it should "delete user folders that are now empty" in {
    val tmpDir = Files.createTempDirectory(testName)
    val sink = new FileSystemSink(tmpDir, "authorized_keys", false)
    sink.write(usersAndKeys)
    sink.write(usersAndKeys.updated("user1", Set.empty[String]))
    tmpDir.toFile.list() shouldBe Array("user2")
  }

}

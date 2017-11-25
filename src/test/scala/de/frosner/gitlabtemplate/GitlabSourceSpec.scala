package de.frosner.gitlabtemplate

import java.net.UnknownHostException

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.RouteDirectives
import com.fasterxml.jackson.core.JsonParseException
import org.scalatest.{FlatSpec, Matchers}

class GitlabSourceSpec extends FlatSpec with Matchers with HttpTests {

  "Getting users through a Gitlab source" should "work" in {
    withServerAndClient {
      path("api" / "v4" / "users") {
        get {
          complete(s"""
               |[
               |  { "id": 1, "username": "usr1" },
               |  { "id": 2, "username": "usr2" }
               |]
             """.stripMargin)
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          val expected = Set(
            GitlabUser(1, "usr1"),
            GitlabUser(2, "usr2")
          )
          new GitlabSource(wsClient, address, "token")
            .getUsers(false)
            .value
            .map(_ shouldBe Right(expected))
      }
    }
  }

  it should "fail if it cannot parse the response" in {
    withServerAndClient {
      path("api" / "v4" / "users") {
        get {
          complete(s"""
                      |[
                      |  { "iddd": 1, "username": "usr1" },
                      |  { "id": 2, "username": "usr2" }
                      |]
             """.stripMargin)
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, address, "token")
            .getUsers(false)
            .value
            .map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if gitlab does not respond" in {
    withServerAndClient { RouteDirectives.reject } { implicit ec =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, "http://dsafdsgdfsfdsfdsf", "token")
            .getUsers(false)
            .value
            .failed
            .map(_ shouldBe a[UnknownHostException])
      }
    }
  }

  "Getting SSH keys through a Gitlab source" should "work" in {
    withServerAndClient {
      path("api" / "v4" / "users" / "1" / "keys") {
        get {
          complete("""[ { "key": "key1" }, { "key": "key2" } ]""")
        }
      } ~ path("api" / "v4" / "users" / "2" / "keys") {
        get {
          complete("""[ { "key": "key3" } ]""")
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          val users = Set(
            GitlabUser(1, "usr1"),
            GitlabUser(2, "usr2")
          )
          val expected = Map(
            "usr1" -> Set("key1", "key2"),
            "usr2" -> Set("key3")
          )
          new GitlabSource(wsClient, address, "token")
            .getSshKeys(users)
            .value
            .map(_ shouldBe Right(expected))
      }
    }
  }

  it should "fail if the keys cannot be parsed" in {
    withServerAndClient {
      path("api" / "v4" / "users" / "1" / "keys") {
        get {
          complete("""[ { "foo": "bar" } ]""")
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, address, "token")
            .getSshKeys(Set(GitlabUser(1, "usr1")))
            .value
            .map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if the keys endpoint does not exist" in {
    withServerAndClient { RouteDirectives.reject } { implicit ec =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, address, "token")
            .getSshKeys(Set(GitlabUser(1, "usr1")))
            .value
            .failed
            .map(_ shouldBe a[JsonParseException])
      }
    }
  }

}

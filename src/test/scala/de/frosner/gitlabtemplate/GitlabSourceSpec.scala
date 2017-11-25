package de.frosner.gitlabtemplate

import java.net.UnknownHostException

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.RouteDirectives
import org.scalatest.{FlatSpec, Matchers}

class GitlabSourceSpec extends FlatSpec with Matchers with HttpTests {

  "A gitlab source" should "list the users" in {
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
          val source = new GitlabSource(wsClient, address, "token")
          val expected = Set(
            GitlabUser(1, "usr1"),
            GitlabUser(2, "usr2")
          )
          source.getUsers(false).value.map(_ shouldBe Right(expected))
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
          val source = new GitlabSource(wsClient, address, "token")
          source.getUsers(false).value.map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if gitlab does not respond" in {
    withServerAndClient { RouteDirectives.reject } { implicit ec =>
      {
        case (wsClient, address) =>
          val source = new GitlabSource(wsClient, "http://dsafdsgdfsfdsfdsf", "token")
          source.getUsers(false).value.failed.map(_ shouldBe a[UnknownHostException])
      }
    }
  }

}

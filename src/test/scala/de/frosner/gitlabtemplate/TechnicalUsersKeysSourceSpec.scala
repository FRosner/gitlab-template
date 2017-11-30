package de.frosner.gitlabtemplate

import java.net.UnknownHostException

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.{Credentials, RouteDirectives}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future
import scala.util
import scala.util.{Failure, Success, Try}

class TechnicalUsersKeysSourceSpec extends FlatSpec with Matchers with HttpTests {

  val authEnabled = HttpBasicAuthConfig(enabled = true, "user", "password")
  val authDisabled = HttpBasicAuthConfig(enabled = false, "", "")

  "A technical users keys source" should "parse the technical users keys correctly" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          complete(s"""
               |authorized-users = {
               |  techusr1 = [usr1]
               |  techusr2 = [usr1, usr2]
               |}
               |
               |authorized-keys = {
               |  techusr2 = ["ssh-rsa techusr2 xxx@yy.zz"]
               |  techusr3 = ["ssh-rsa techusr3 xxx@yy.zz"]
               |}
             """.stripMargin)
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient, s"$address/$technicalUsersFileName", authDisabled)
          val expected = TechnicalUsersKeys(
            authorizedUsers = Map(
              "techusr1" -> Set("usr1"),
              "techusr2" -> Set("usr1", "usr2")
            ),
            authorizedKeys = Map(
              "techusr2" -> Set("ssh-rsa techusr2 xxx@yy.zz"),
              "techusr3" -> Set("ssh-rsa techusr3 xxx@yy.zz")
            )
          )
          source.get.value.map(_ shouldBe Right(expected))
      }
    }
  }

  it should "authenticate if required" in {
    val technicalUsersFileName = "technical-users.conf"
    def authenticator(credentials: Credentials): Option[String] =
      credentials match {
        case p @ Credentials.Provided(id) if p.verify(authEnabled.password) => Some(id)
        case _                                                              => None
      }
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          authenticateBasic(realm = "secure", authenticator) { userName =>
            complete(s"""
                      |authorized-users = {
                      |  techusr1 = [usr1]
                      |  techusr2 = [usr1, usr2]
                      |}
                      |
                      |authorized-keys = {
                      |  techusr2 = ["ssh-rsa techusr2 xxx@yy.zz"]
                      |  techusr3 = ["ssh-rsa techusr3 xxx@yy.zz"]
                      |}
             """.stripMargin)
          }
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient, s"$address/$technicalUsersFileName", authEnabled)
          val expected = TechnicalUsersKeys(
            authorizedUsers = Map(
              "techusr1" -> Set("usr1"),
              "techusr2" -> Set("usr1", "usr2")
            ),
            authorizedKeys = Map(
              "techusr2" -> Set("ssh-rsa techusr2 xxx@yy.zz"),
              "techusr3" -> Set("ssh-rsa techusr3 xxx@yy.zz")
            )
          )
          source.get.value.map(_ shouldBe Right(expected))
      }
    }
  }

  it should "fail if authentication fails" in {
    val technicalUsersFileName = "technical-users.conf"
    def authenticator(credentials: Credentials): Option[String] =
      credentials match {
        case p @ Credentials.Provided(id) if p.verify(authEnabled.password) => Some(id)
        case _                                                              => None
      }
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          authenticateBasic(realm = "secure", authenticator) { userName =>
            complete("")
          }
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient, s"$address/$technicalUsersFileName", authDisabled)
          source.get.value.failed.map(_ shouldBe a[AuthenticationException])
      }
    }
  }

  it should "fail if the technical users keys cannot be parsed" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          complete(s"""
                      |authorized-users = {
                      |}
             """.stripMargin)
        }
      }
    } { implicit ec =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient, s"$address/$technicalUsersFileName", authDisabled)
          source.get.value.map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if the technical users keys does not exist" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient { RouteDirectives.reject } { implicit ec =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"http://doesnotexistIhope:12345/$technicalUsersFileName",
                                                    authDisabled)
          source.get.value.failed.map(_ shouldBe a[UnknownHostException])
      }
    }
  }

}

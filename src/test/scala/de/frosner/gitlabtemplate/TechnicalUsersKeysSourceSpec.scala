package de.frosner.gitlabtemplate

import java.net.UnknownHostException

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.{Credentials, RouteDirectives}
import org.scalatest.{FlatSpec, Matchers}

class TechnicalUsersKeysSourceSpec extends FlatSpec with Matchers with HttpTests {

  private val basicAuthEnabled = HttpBasicAuthConfig(enabled = true, "user", "password")
  private val basicAuthDisabled = HttpBasicAuthConfig(enabled = false, "", "")
  private val tokenAuthEnabled = PrivateTokenAuthConfig(enabled = true, "token")
  private val tokenAuthDisabled = PrivateTokenAuthConfig(enabled = false, "token")

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
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthDisabled)
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

  it should "authenticate via basic auth if required" in {
    val technicalUsersFileName = "technical-users.conf"
    def authenticator(credentials: Credentials): Option[String] =
      credentials match {
        case p @ Credentials.Provided(id) if p.verify(basicAuthEnabled.password) => Some(id)
        case _                                                                   => None
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
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthEnabled,
                                                    tokenAuthDisabled)
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

  it should "fail if basic auth authentication fails" in {
    val technicalUsersFileName = "technical-users.conf"
    def authenticator(credentials: Credentials): Option[String] =
      credentials match {
        case p @ Credentials.Provided(id) if p.verify(basicAuthEnabled.password) => Some(id)
        case _                                                                   => None
      }
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          authenticateBasic(realm = "secure", authenticator) { userName =>
            complete(StatusCodes.OK)
          }
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthDisabled)
          source.get.value.failed.map(_ shouldBe a[AuthenticationException])
      }
    }
  }

  it should "authenticate via private token if required" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          headerValueByName("PRIVATE-TOKEN") { token =>
            if (token == tokenAuthEnabled.token)
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
            else
              complete(StatusCodes.Unauthorized)
          }
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthEnabled)
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

  it should "fail if the wrong token is provided" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          headerValueByName("PRIVATE-TOKEN") { token =>
            if (token == tokenAuthEnabled.token)
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
            else
              complete(StatusCodes.Unauthorized)
          }
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthEnabled.copy(token = "wrong"))
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
          source.get.value.failed.map(_ shouldBe a[AuthenticationException])
      }
    }
  }

  it should "fail if the response is not 200" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient {
      path(technicalUsersFileName) {
        get {
          complete(StatusCodes.BadRequest)
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthDisabled)
          source.get.value.failed.map(_ shouldBe a[UnexpectedStatusCodeException])
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
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"$address/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthDisabled)
          source.get.value.map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if the technical users keys does not exist" in {
    val technicalUsersFileName = "technical-users.conf"
    withServerAndClient { RouteDirectives.reject } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val source = new TechnicalUsersKeysSource(wsClient,
                                                    s"http://doesnotexistIhope:12345/$technicalUsersFileName",
                                                    basicAuthDisabled,
                                                    tokenAuthDisabled)
          source.get.value.failed.map(_ shouldBe a[UnknownHostException])
      }
    }
  }

}

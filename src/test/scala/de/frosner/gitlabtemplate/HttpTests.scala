package de.frosner.gitlabtemplate

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import org.scalatest.{Assertion, AsyncFlatSpec}
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait HttpTests {

  def withServerAndClient(route: Route)(
      assertionGen: ExecutionContext => (StandaloneWSClient, String) => Future[Assertion]): Future[Assertion] = {
    implicit val system = ActorSystem(this.getClass.getSimpleName + UUID.randomUUID().toString)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    // prepare HTTP server and client
    val bindingFuture = Http().bindAndHandle(RouteResult.route2HandlerFlow(route), "localhost", 0)
    val bindingAddress = Await.result(bindingFuture.map(_.localAddress), 2.seconds)
    val wsClient = StandaloneAhcWSClient()

    // run the assertion and wait for it to complete
    val assertion =
      assertionGen(executionContext)(wsClient, s"http://${bindingAddress.getHostName}:${bindingAddress.getPort}")
    Await.ready(assertion, 5.seconds)

    // cleanup
    wsClient.close()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

    assertion
  }

}

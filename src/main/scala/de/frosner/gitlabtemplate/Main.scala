package de.frosner.gitlabtemplate

import java.util.concurrent._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{Scheduler, UncaughtExceptionReporter}
import play.api.libs.ws.ahc._
import pureconfig._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main extends StrictLogging {

  type PublicKeyType = String
  type Username = String

  def main(args: Array[String]): Unit = {
    val conf = loadConfigOrThrow[GitlabTemplateConfig](ConfigFactory.load().getConfig("gitlab-template"))
    val gitlabConf = conf.source.gitlab
    val filesystemConf = conf.sink.filesystem

    implicit val system = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }
    implicit val materializer = ActorMaterializer()

    val wsClient = StandaloneAhcWSClient()

    sys.addShutdownHook {
      system.terminate()
      wsClient.close()
    }

    val gitlabClient =
      new GitlabSource(wsClient, gitlabConf.url, gitlabConf.privateToken, gitlabConf.onlyActiveUsers)

    val technicalUsersKeysSource = new TechnicalUsersKeysSource(wsClient, conf.source.technicalUsersKeys.url)

    val filesystemSink =
      new FileSystemSink(filesystemConf.path, filesystemConf.publicKeysFile, filesystemConf.createEmptyKeyFile)

    val pipeline = new KeyPipeline(gitlabClient, technicalUsersKeysSource, filesystemSink, conf.dryRun)

    val executorService = scala.concurrent.ExecutionContext.Implicits.global
    val scheduler =
      Scheduler(
        Executors.newSingleThreadScheduledExecutor(),
        executorService,
        UncaughtExceptionReporter(executorService.reportFailure),
        AlwaysAsyncExecution
      )
    scheduler.scheduleWithFixedDelay(0.seconds, conf.renderFrequency) {
      pipeline
        .generateKeys(conf.timeout)
        .failed
        .foreach(_ => System.exit(1)) // TODO proper error handling, catching the error once and handle it once in the end
    }
  }

}

package de.frosner.gitlabtemplate

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

case class GitlabSettings(requireActive: Boolean, privateToken: String, pollingFreqSec: Long, url: String) {

  val flatToString: Seq[(String, String)] = Seq(
    ("requireActive", requireActive.toString),
    ("privateToken", "********"),
    ("pollingFreqSec", s"${pollingFreqSec}s"),
    ("url", url)
  )

}

object GitlabSettings {

  def fromConfig(config: Config): GitlabSettings = GitlabSettings(
    requireActive = config.getBoolean("onlyActiveUsers"),
    privateToken = config.getString("privateToken"),
    pollingFreqSec = config.getDuration("pollingFrequency", TimeUnit.SECONDS),
    url = config.getString("url")
  )

}

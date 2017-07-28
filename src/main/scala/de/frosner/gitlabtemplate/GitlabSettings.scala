/*
 * Copyright 2017 Frank Rosner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

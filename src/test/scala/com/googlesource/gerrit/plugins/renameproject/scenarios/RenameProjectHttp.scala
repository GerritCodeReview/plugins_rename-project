// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.renameproject.scenarios
import com.google.gerrit.scenarios.GitSimulation
import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

class RenameProjectHttp extends GitSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private var projectRenamed = projectName + "-renamed"
  def this(projectName: String) {
    this()
    this.projectName = projectName
    this.projectRenamed = projectName + "-renamed"
  }
  override def replaceOverride(in: String): String = {
    var next = replaceKeyWith("_project", projectName, in)
    next = replaceKeyWith("plugin_name", "rename-project", next)
    next = replaceKeyWith("action", "rename", next)
    super.replaceOverride(next)
  }
  val test: ScenarioBuilder = scenario(uniqueName)
      .feed(data)
      .exec(session => {
        session.set("name", projectRenamed)
      })
      .exec(httpRequest
          .body(ElFileBody(body)).asJson)
  setUp(
    test.inject(
      atOnceUsers(single)
    )
  ).protocols(httpProtocol)
}

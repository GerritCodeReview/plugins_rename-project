package com.googlesource.gerrit.plugins.renameproject.scenarios

import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import scala.concurrent.duration._

class RenameProject extends GerritSimulation {
  private val project: String = "GATLING_RENAME_TEST"
  private val projectRenamed: String = "GATLING_RENAME_TEST_RENAMED"

  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue

  override def replaceOverride(in: String): String = {
    var next = replaceKeyWith("project", project, in)
    next = replaceKeyWith("renamed", projectRenamed, next)
    super.replaceOverride(next)
  }
  private val createProject = new CreateProject(project)
  private val deleteProject = new DeleteProject(projectRenamed)


  private val test: ScenarioBuilder = scenario(unique)
      .feed(data)
      .exec(httpRequest.
          body(ElFileBody(body)).asJson)

  setUp(
    createProject.test.inject(
      atOnceUsers(1)
    ),
    test.inject(
      nothingFor(5 seconds),
      atOnceUsers(1)
    ),
    deleteProject.test.inject(
      nothingFor(10 seconds),
      atOnceUsers(1)
    )
  ).protocols(httpProtocol)
}

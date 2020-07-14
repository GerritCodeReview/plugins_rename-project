// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.renameproject;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.monitor.CommandProgressMonitor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(RenameProjectCapability.RENAME_PROJECT)
@Export("/rename")
@Singleton
public class RenameProjectServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final RenameProject renameProject;
  private final Provider<ProjectCache> projectCacheProvider;

  @Inject
  RenameProjectServlet(
      RenameProject renameProject,
      Provider<ProjectCache> projectCacheProvider,
      Provider<CurrentUser> self) {
    this.renameProject = renameProject;
    this.projectCacheProvider = projectCacheProvider;
    this.self = self;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    StringWriter stringWriter = new StringWriter();
    try {
      BufferedReader reader = req.getReader();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      Map<String, String> input =
          new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
      renameProject(
          input.get("newProjectName"), input.get("oldProjectName"), printWriter, self.get());
      res.setStatus(SC_NO_CONTENT);
    } catch (AuthException
        | BadRequestException
        | ResourceConflictException
        | NoSuchProjectException e) {
      res.sendError(SC_BAD_REQUEST);
      logger.atSevere().withCause(e).log(e.getMessage());
    } catch (ConfigInvalidException
        | RenameRevertException
        | InterruptedException
        | IOException e) {
      res.sendError(SC_INTERNAL_SERVER_ERROR);
      logger.atSevere().withCause(e).log(
          String.format("Failed to rename project. Performed steps: %s", stringWriter.toString()));
    }
  }

  private void renameProject(
      String newProjectName, String oldProjectName, PrintWriter stdout, CurrentUser user)
      throws AuthException, BadRequestException, ResourceConflictException, IOException,
          InterruptedException, ConfigInvalidException, RenameRevertException,
          NoSuchProjectException {
    RenameProject.Input input = new RenameProject.Input();
    input.name = newProjectName;
    ProjectState oldProjectState = projectCacheProvider.get().get(Project.nameKey(oldProjectName));
    if (oldProjectState != null) {
      ProjectResource rsrc = new ProjectResource(oldProjectState, user);
      try (CommandProgressMonitor monitor = new CommandProgressMonitor(stdout)) {
        renameProject.assertCanRename(rsrc, input, monitor);
        List<Id> changeIds = renameProject.getChanges(rsrc, monitor);
        renameProject.doRename(changeIds, rsrc, input, monitor);
      }
    } else {
      throw new NoSuchProjectException(Project.nameKey(oldProjectName));
    }
  }
}

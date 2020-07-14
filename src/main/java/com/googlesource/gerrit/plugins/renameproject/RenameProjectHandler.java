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

import static com.googlesource.gerrit.plugins.renameproject.RenameProject.WARNING_LIMIT;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.renameproject.monitor.CommandProgressMonitor;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenameProjectHandler {
  private static final Logger log = LoggerFactory.getLogger(RenameProject.class);
  private final RenameProject renameProject;
  private final Provider<ProjectCache> projectCacheProvider;
  private final Provider<CurrentUser> self;

  @Inject
  protected RenameProjectHandler(
      RenameProject renameProject,
      Provider<ProjectCache> projectCacheProvider,
      Provider<CurrentUser> self) {
    this.renameProject = renameProject;
    this.projectCacheProvider = projectCacheProvider;
    this.self = self;
  }

  void renameProject(
      String newProjectName, String oldProjectName, PrintWriter stdout, InputStream in)
      throws AuthException, BadRequestException, ResourceConflictException, IOException,
          InterruptedException, ConfigInvalidException, RenameRevertException,
          NoSuchProjectException {
    RenameProject.Input input = new RenameProject.Input();
    input.name = newProjectName;
    ProjectState oldProjectState = projectCacheProvider.get().get(Project.nameKey(oldProjectName));
    if (oldProjectState != null) {
      ProjectResource rsrc = new ProjectResource(oldProjectState, self.get());
      try (CommandProgressMonitor monitor = new CommandProgressMonitor(stdout)) {
        renameProject.assertCanRename(rsrc, input, monitor);
        List<Id> changeIds = renameProject.getChanges(rsrc, monitor);
        if (continueRename(changeIds, monitor, stdout, in)) {
          renameProject.doRename(changeIds, rsrc, input, monitor);
        } else {
          String cancellationMsg = "Rename operation was cancelled by user.";
          log.debug(cancellationMsg);
          stdout.println(cancellationMsg);
          stdout.flush();
        }
      }
    } else {
      throw new NoSuchProjectException(Project.nameKey(oldProjectName));
    }
  }

  private boolean continueRename(
      List<Change.Id> changes, ProgressMonitor pm, PrintWriter stdout, InputStream in)
      throws IOException {
    if (changes != null && changes.size() > WARNING_LIMIT) {
      // close the progress task explicitly this time to get user input
      pm.close();
      stdout.print(
          String.format(
              "\nThis project contains %d changes and renaming the project will take longer time.\n"
                  + "Do you still want to continue? [y/N]: ",
              changes.size()));
      stdout.flush();
      try (BufferedReader input = new BufferedReader(new InputStreamReader(in))) {
        String userInput = input.readLine();
        return userInput.toLowerCase().startsWith("y");
      }
    }
    return true;
  }
}

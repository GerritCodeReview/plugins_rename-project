package com.googlesource.gerrit.plugins.renameproject;
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

import com.google.gerrit.entities.Change.Id;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.renameproject.monitor.CommandProgressMonitor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class RenameProjectHandler {
  private final RenameProject renameProject;
  private final Provider<ProjectCache> projectCacheProvider;

  @Inject
  protected RenameProjectHandler(
      RenameProject renameProject, Provider<ProjectCache> projectCacheProvider) {
    this.renameProject = renameProject;
    this.projectCacheProvider = projectCacheProvider;
  }

  public void renameProject(
      String newProjectName, String oldProjectName, PrintWriter stdout, CurrentUser user)
      throws AuthException, BadRequestException, ResourceConflictException, IOException,
          InterruptedException, ConfigInvalidException, RenameRevertException,
          NoSuchProjectException {
    RenameProject.Input input = new RenameProject.Input();
    input.name = newProjectName;
    Optional<ProjectState> oldProjectState =
        projectCacheProvider.get().get(Project.nameKey(oldProjectName));
    if (oldProjectState.isPresent()) {
      ProjectResource rsrc = new ProjectResource(oldProjectState.get(), user);
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

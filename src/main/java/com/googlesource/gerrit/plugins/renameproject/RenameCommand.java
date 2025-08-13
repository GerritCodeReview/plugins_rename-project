// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.renameproject.monitor.CommandProgressMonitor;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;
import java.util.Set;
import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandMetaData(name = "rename", description = "Rename project")
public final class RenameCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "OLDPROJECT", usage = "project to rename")
  private ProjectState projectState;

  @Argument(index = 1, required = true, metaVar = "NEWNAME", usage = "new name for the project")
  private String newProjectName;

  private static final Logger log = LoggerFactory.getLogger(RenameCommand.class);
  private final RenameProject renameProject;
  private final Provider<CurrentUser> self;
	private final DynamicSet<ProjectDeletedListener> deletedListeners;

  @Inject
  protected RenameCommand(
			RenameProject renameProject,
			Provider<CurrentUser> self,
      DynamicSet<ProjectDeletedListener> deletedListeners) {
    this.renameProject = renameProject;
    this.self = self;
		this.deletedListeners = deletedListeners;
  }

  @Override
  public void run() throws Exception {
    try {
      RenameProject.Input input = new RenameProject.Input();
      input.name = newProjectName;
      ProjectResource rsrc = new ProjectResource(projectState, self.get());
      try (CommandProgressMonitor monitor = new CommandProgressMonitor(stdout)) {
        renameProject.assertCanRename(rsrc, input, monitor);
        Set<Change.Id> changeIds = renameProject.getChanges(rsrc, monitor);
        if (!renameProject.startRename(
            rsrc, input, monitor, continueRename(changeIds, monitor), changeIds, deletedListeners)) {
          stdout.flush();
        }
      }
    } catch (NoSuchElementException | RestApiException | IOException e) {
      throw die(e);
    }
  }

  private boolean continueRename(Set<Change.Id> changes, ProgressMonitor pm) throws IOException {
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

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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandMetaData(name = "rename", description = "Rename project")
public final class RenameCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "OLDPROJECT", usage = "project to rename")
  private String projectControl;

  @Argument(index = 1, required = true, metaVar = "NEWNAME", usage = "new name for the project")
  private String newProjectName;

  private static final Logger log = LoggerFactory.getLogger(RenameCommand.class);
  private final RenameProjectHandler renameProjectHandler;

  @Inject
  protected RenameCommand(RenameProjectHandler renameProjectHandler) {
    this.renameProjectHandler = renameProjectHandler;
  }

  @Override
  public void run() throws Exception{
    try {
      renameProjectHandler.renameProject(newProjectName, projectControl, stdout, in);
    } catch (RestApiException
        | IOException
        | InterruptedException
        | ConfigInvalidException
        | RenameRevertException
        | NoSuchProjectException e) {
      throw die(e);
    }
  }
}

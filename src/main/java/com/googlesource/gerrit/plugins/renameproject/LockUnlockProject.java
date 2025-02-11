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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class LockUnlockProject {
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  LockUnlockProject(
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache,
      ProjectConfig.Factory projectConfigFactory) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.projectConfigFactory = projectConfigFactory;
  }

  public void lock(Project.NameKey key) throws IOException, ConfigInvalidException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(key)) {

      ProjectConfig projectConfig = projectConfigFactory.read(md);
      projectConfig.updateProject(project -> project.setState(ProjectState.READ_ONLY));

      md.setMessage(String.format("Lock project while renaming the project %s\n", key.get()));
      projectConfig.commit(md);
      Project p = projectConfig.getProject();
      projectCache.evict(p.getNameKey());
    }
  }

  public void unlock(Project.NameKey key) throws IOException, ConfigInvalidException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(key)) {

      ProjectConfig projectConfig = projectConfigFactory.read(md);
      projectConfig.updateProject(project -> project.setState(ProjectState.ACTIVE));

      md.setMessage(String.format("Unlock project after renaming the project to %s\n", key.get()));
      projectConfig.commit(md);
      Project p = projectConfig.getProject();
      projectCache.evict(p.getNameKey());
    }
  }
}

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

package com.googlesource.gerrit.plugins.renameproject.cache;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class CacheRenameHandler {

  private final ProjectCache projectCache;

  @Inject
  public CacheRenameHandler(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public void update(Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws IOException {
    projectCache.remove(oldProjectKey);
    projectCache.evict(newProjectKey);
    projectCache.onCreateProject(newProjectKey);
  }
}

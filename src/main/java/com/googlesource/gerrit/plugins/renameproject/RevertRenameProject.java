// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.renameproject.RenameProject.Step;
import com.googlesource.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.googlesource.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevertRenameProject {

  private static final Logger log = LoggerFactory.getLogger(RevertRenameProject.class);

  private final DatabaseRenameHandler dbHandler;
  private final FilesystemRenameHandler fsHandler;
  private final CacheRenameHandler cacheHandler;
  private final IndexUpdateHandler indexHandler;

  @Inject
  RevertRenameProject(
      DatabaseRenameHandler dbHandler,
      FilesystemRenameHandler fsHandler,
      CacheRenameHandler cacheHandler,
      IndexUpdateHandler indexHandler) {
    this.dbHandler = dbHandler;
    this.fsHandler = fsHandler;
    this.cacheHandler = cacheHandler;
    this.indexHandler = indexHandler;
  }

  void performRevert(
      List<Step> stepsPerformed,
      List<Id> changeIds,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      Optional<ProgressMonitor> opm)
      throws IOException, OrmException, ConfigInvalidException {
    opm.ifPresent(pm -> pm.beginTask("Reverting the rename procedure."));
    List<Change.Id> updatedChangeIds = Collections.emptyList();
    if (stepsPerformed.contains(Step.FILESYSTEM)) {
      try {
        fsHandler.rename(newProjectKey, oldProjectKey, opm);
        log.debug("Reverted the git repo name to {} successfully.", oldProjectKey.get());
      } catch (IOException e) {
        log.error(
            "Failed to revert git repo name. Aborting revert. Exception caught: {}", e.toString());
        throw e;
      }
    }
    if (stepsPerformed.contains(Step.CACHE)) {
      cacheHandler.update(newProjectKey, oldProjectKey);
      log.debug("Successfully removed project {} from project cache.", newProjectKey.get());
    }
    if (stepsPerformed.contains(Step.DATABASE)) {
      try {

        updatedChangeIds = dbHandler.rename(changeIds, newProjectKey, oldProjectKey, opm);
        log.debug(
            "Reverted the changes in DB successfully from project {} to project {}.",
            newProjectKey.get(),
            oldProjectKey.get());
      } catch (OrmException e) {
        log.error(
            "Failed to revert changes in DB for project {}. Secondary indexes not reverted. Exception caught: {}",
            oldProjectKey.get(),
            e.toString());
        throw e;
      }
    }
    if (stepsPerformed.contains(Step.INDEX)) {
      try {
        indexHandler.updateIndex(updatedChangeIds, oldProjectKey, opm);
        log.debug(
            "Reverted the secondary index successfully from project {} to project {}.",
            newProjectKey.get(),
            oldProjectKey.get());
      } catch (InterruptedException e) {
        log.error(
            "Secondary index revert failed for {}. Exception caught: {}",
            oldProjectKey.get(),
            e.toString());
      }
    }
  }
}

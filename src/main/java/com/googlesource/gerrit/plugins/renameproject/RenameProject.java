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

import static com.googlesource.gerrit.plugins.renameproject.RenameOwnProjectCapability.RENAME_OWN_PROJECT;
import static com.googlesource.gerrit.plugins.renameproject.RenameProjectCapability.RENAME_PROJECT;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.PluginEvent;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.conditions.RenamePreconditions;
import com.googlesource.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.googlesource.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RenameProject {

  static class Input {
    String name;
  }

  static final int WARNING_LIMIT = 5000;
  private static final Logger log = LoggerFactory.getLogger(RenameProject.class);
  private static final String CACHE_NAME = "changeid_project";

  private final DatabaseRenameHandler dbHandler;
  private final FilesystemRenameHandler fsHandler;
  private final CacheRenameHandler cacheHandler;
  private final RenamePreconditions renamePreconditions;
  private final IndexUpdateHandler indexHandler;
  private final Provider<CurrentUser> userProvider;
  private final LockUnlockProject lockUnlockProject;
  private final PluginEvent pluginEvent;
  private final String pluginName;
  private final RenameLog renameLog;
  private final PermissionBackend permissionBackend;
  private final Cache<Change.Id, String> changeIdProjectCache;

  private List<Step> stepsPerformed;

  @Inject
  RenameProject(
      DatabaseRenameHandler dbHandler,
      FilesystemRenameHandler fsHandler,
      CacheRenameHandler cacheHandler,
      RenamePreconditions renamePreconditions,
      IndexUpdateHandler indexHandler,
      Provider<CurrentUser> userProvider,
      LockUnlockProject lockUnlockProject,
      PluginEvent pluginEvent,
      @PluginName String pluginName,
      RenameLog renameLog,
      PermissionBackend permissionBackend,
      @Named(CACHE_NAME) Cache<Change.Id, String> changeIdProjectCache) {
    this.dbHandler = dbHandler;
    this.fsHandler = fsHandler;
    this.cacheHandler = cacheHandler;
    this.renamePreconditions = renamePreconditions;
    this.indexHandler = indexHandler;
    this.userProvider = userProvider;
    this.lockUnlockProject = lockUnlockProject;
    this.pluginEvent = pluginEvent;
    this.pluginName = pluginName;
    this.renameLog = renameLog;
    this.permissionBackend = permissionBackend;
    this.changeIdProjectCache = changeIdProjectCache;
  }

  private void assertNewNameNotNull(Input input) throws BadRequestException {
    if (input == null || Strings.isNullOrEmpty(input.name)) {
      throw new BadRequestException("Name of the repo cannot be null or empty");
    }
  }

  private void assertRenamePermission(ProjectResource rsrc) throws AuthException {
    if (!canRename(rsrc)) {
      throw new AuthException("Not allowed to rename project");
    }
  }

  protected boolean canRename(ProjectResource rsrc) {
    PermissionBackend.WithUser userPermission = permissionBackend.user(userProvider);
    return userPermission.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER)
        || userPermission.testOrFalse(new PluginPermission(pluginName, RENAME_PROJECT))
        || (userPermission.testOrFalse(new PluginPermission(pluginName, RENAME_OWN_PROJECT))
            && rsrc.getControl().isOwner());
  }

  void assertCanRename(ProjectResource rsrc, Input input, ProgressMonitor pm)
      throws ResourceConflictException, BadRequestException, AuthException {
    try {
      pm.beginTask("Checking preconditions");
      assertNewNameNotNull(input);
      assertRenamePermission(rsrc);
      renamePreconditions.assertCanRename(rsrc, new Project.NameKey(input.name));
      log.debug("Rename preconditions check successful.");
    } catch (CannotRenameProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  void doRename(List<Change.Id> changeIds, ProjectResource rsrc, Input input, ProgressMonitor pm)
      throws InterruptedException, OrmException, ConfigInvalidException, IOException {
    Project.NameKey oldProjectKey = rsrc.getControl().getProject().getNameKey();
    Project.NameKey newProjectKey = new Project.NameKey(input.name);
    Exception ex = null;
    stepsPerformed = new ArrayList<>();
    try {
      fsHandler.rename(oldProjectKey, newProjectKey, pm);
      logSuccessStep(Step.FILESYSTEM, newProjectKey, oldProjectKey);

      cacheHandler.update(rsrc.getControl().getProject().getNameKey(), newProjectKey);
      logSuccessStep(Step.CACHE, newProjectKey, oldProjectKey);

      List<Change.Id> updatedChangeIds =
          dbHandler.rename(changeIds, oldProjectKey, newProjectKey, pm);
      logSuccessStep(Step.DATABASE, newProjectKey, oldProjectKey);
      // if the DB update is successful, update the secondary index
      indexHandler.updateIndex(updatedChangeIds, newProjectKey, pm);
      logSuccessStep(Step.INDEX, newProjectKey, oldProjectKey);

      // no need to revert this since newProjectKey will be removed from project cache before
      lockUnlockProject.unlock(newProjectKey);
      log.debug("Unlocked the repo {} after rename operation.", newProjectKey.get());

      // flush old changeId -> Project cache for given changeIds
      changeIdProjectCache.invalidateAll(changeIds);

      pluginEvent.fire(pluginName, pluginName, oldProjectKey.get() + ":" + newProjectKey.get());
    } catch (Exception e) {
      log.error(
          "Renaming procedure failed after successful step {}. Exception caught: {}",
          stepsPerformed.get(stepsPerformed.size() - 1).toString(),
          e.toString());
      try {
        performRevert(stepsPerformed, changeIds, oldProjectKey, newProjectKey, pm);
      } catch (Exception revertEx) {
        log.error(
            "Failed to revert renaming procedure for {}. Exception caught: {}",
            oldProjectKey.get(),
            revertEx.toString());
        throw new RenameRevertException(revertEx, e);
      }

      throw e;
    } finally {
      renameLog.onRename((IdentifiedUser) userProvider.get(), oldProjectKey, input, ex);
    }
  }

  private enum Step {
    FILESYSTEM,
    CACHE,
    DATABASE,
    INDEX
  }

  private void logSuccessStep(
      Step step, Project.NameKey newProjectKey, Project.NameKey oldProjectKey) {
    switch (step) {
      case FILESYSTEM:
        log.debug("Renamed the git repo to {} successfully.", newProjectKey.get());
        break;
      case DATABASE:
        log.debug("Updated the changes in DB successfully for project {}.", oldProjectKey.get());
        break;
      case INDEX:
        log.debug("Updated the secondary index successfully for project {}.", oldProjectKey.get());
        break;
      default:
        log.debug("Rename step: {} completed successfully.", step.toString());
    }
    stepsPerformed.add(step);
  }

  private void performRevert(
      List<Step> toRevert,
      List<Change.Id> changeIds,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      ProgressMonitor pm)
      throws IOException, OrmException {
    pm.beginTask("Reverting the rename procedure.");
    if (toRevert.contains(Step.FILESYSTEM)) {
      try {
        fsHandler.rename(newProjectKey, oldProjectKey, pm);
        log.debug("FILESYSTEM: Renamed the git repo to {} successfully.", oldProjectKey.get());
      } catch (IOException e) {
        log.error(
            "FILESYSTEM: Failed to perform FS revert. Aborting revert. Exception caught: {}",
            e.toString());
        throw e;
      }
    }
    if (toRevert.contains(Step.CACHE)) {
      cacheHandler.update(newProjectKey, oldProjectKey);
      log.debug("CACHE: Successfully removed project {} from project cache", newProjectKey.get());
    }
    List<Change.Id> updatedChangeIds = Collections.emptyList();
    if (toRevert.contains(Step.DATABASE)) {
      try {
        updatedChangeIds = dbHandler.rename(changeIds, newProjectKey, oldProjectKey, pm);
        log.debug(
            "DATABASE: Updated the changes in DB successfully from project {} to project {}.",
            newProjectKey.get(),
            oldProjectKey.get());
      } catch (OrmException e) {
        log.error(
            "DATABASE: Failed to Step changes in DB for project {}. Exception caught: {}"
                + "\nSecondary indexes are not going to be reverted",
            oldProjectKey.get(),
            e.toString());
        throw e;
      }
    }
    if (toRevert.contains(Step.INDEX)) {
      try {
        indexHandler.updateIndex(updatedChangeIds, oldProjectKey, pm);
        log.debug(
            "INDEX: Updated the secondary index successfully from project {} to project {}.",
            newProjectKey.get(),
            oldProjectKey.get());
      } catch (InterruptedException e) {
        log.error(
            "INDEX: Secondary index update failed for {}. Exception caught: {}",
            oldProjectKey.get(),
            e.toString());
      }
    }
  }

  List<Change.Id> getChanges(ProjectResource rsrc, ProgressMonitor pm)
      throws OrmException, IOException {
    pm.beginTask("Retrieving the list of changes from DB");
    Project.NameKey oldProjectKey = rsrc.getControl().getProject().getNameKey();
    return dbHandler.getChangeIds(oldProjectKey);
  }
}

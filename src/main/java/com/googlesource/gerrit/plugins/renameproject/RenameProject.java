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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.PluginEvent;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.renameproject.RenameProject.Input;
import com.googlesource.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.conditions.RenamePreconditions;
import com.googlesource.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.googlesource.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RenameProject implements RestModifyView<ProjectResource, Input> {

  @Override
  public Object apply(ProjectResource resource, Input input)
      throws IOException, AuthException, BadRequestException, ResourceConflictException,
          InterruptedException, ConfigInvalidException, RenameRevertException {
    assertCanRename(resource, input, Optional.empty());
    List<Id> changeIds = getChanges(resource, Optional.empty());
    if (changeIds == null || changeIds.size() <= WARNING_LIMIT || input.continueWithRename) {
      doRename(changeIds, resource, input, Optional.empty());
    } else {
      String cancellationMsg =
          "Rename operation via HTTP was cancelled due to number of changes exceeding warning limit.";
      log.debug(cancellationMsg);
      return Response.ok(cancellationMsg);
    }
    return Response.ok("");
  }

  static class Input {
    String name;
    boolean continueWithRename;
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
  private final RevertRenameProject revertRenameProject;

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
      @Named(CACHE_NAME) Cache<Change.Id, String> changeIdProjectCache,
      RevertRenameProject revertRenameProject) {
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
    this.revertRenameProject = revertRenameProject;
    this.stepsPerformed = new ArrayList<>();
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
    PermissionBackend.WithUser userPermission = permissionBackend.user(userProvider.get());
    return userPermission.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER)
        || userPermission.testOrFalse(new PluginPermission(pluginName, RENAME_PROJECT))
        || (userPermission.testOrFalse(new PluginPermission(pluginName, RENAME_OWN_PROJECT))
            && isOwner(rsrc));
  }

  private boolean isOwner(ProjectResource project) {
    try {
      permissionBackend
          .user(project.getUser())
          .project(project.getNameKey())
          .check(ProjectPermission.WRITE_CONFIG);
    } catch (AuthException | PermissionBackendException noWriter) {
      try {
        permissionBackend.user(project.getUser()).check(GlobalPermission.ADMINISTRATE_SERVER);
      } catch (AuthException | PermissionBackendException noAdmin) {
        return false;
      }
    }
    return true;
  }

  void assertCanRename(ProjectResource rsrc, Input input, Optional<ProgressMonitor> pm)
      throws ResourceConflictException, BadRequestException, AuthException {
    try {
      pm.ifPresent(progressMonitor -> progressMonitor.beginTask("Checking preconditions"));

      assertNewNameNotNull(input);
      assertRenamePermission(rsrc);
      renamePreconditions.assertCanRename(rsrc, new Project.NameKey(input.name));
      log.debug("Rename preconditions check successful.");
    } catch (CannotRenameProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  void doRename(
      List<Change.Id> changeIds, ProjectResource rsrc, Input input, Optional<ProgressMonitor> pm)
      throws InterruptedException, ConfigInvalidException, IOException, RenameRevertException {
    Project.NameKey oldProjectKey = rsrc.getNameKey();
    Project.NameKey newProjectKey = new Project.NameKey(input.name);
    Exception ex = null;
    stepsPerformed.clear();
    try {
      fsRenameStep(oldProjectKey, newProjectKey, pm);

      cacheRenameStep(rsrc.getNameKey(), newProjectKey);

      List<Change.Id> updatedChangeIds = dbRenameStep(changeIds, oldProjectKey, newProjectKey, pm);

      // if the DB update is successful, update the secondary index
      indexRenameStep(updatedChangeIds, oldProjectKey, newProjectKey, pm);

      // no need to revert this since newProjectKey will be removed from project cache before
      lockUnlockProject.unlock(newProjectKey);
      log.debug("Unlocked the repo {} after rename operation.", newProjectKey.get());

      // flush old changeId -> Project cache for given changeIds
      changeIdProjectCache.invalidateAll(changeIds);

      pluginEvent.fire(pluginName, pluginName, oldProjectKey.get() + ":" + newProjectKey.get());
    } catch (Exception e) {
      if (stepsPerformed.isEmpty()) {
        log.error("Renaming procedure failed. Exception caught: {}", e.toString());
      } else {
        log.error(
            "Renaming procedure failed, last successful step {}. Exception caught: {}",
            stepsPerformed.get(stepsPerformed.size() - 1).toString(),
            e.toString());
      }
      try {
        revertRenameProject.performRevert(
            stepsPerformed, changeIds, oldProjectKey, newProjectKey, pm);
      } catch (Exception revertEx) {
        log.error(
            "Failed to revert renaming procedure for {}. Exception caught: {}",
            oldProjectKey.get(),
            revertEx.toString());
        ex = revertEx;
        throw new RenameRevertException(revertEx, e);
      }
      ex = e;
      throw e;
    } finally {
      renameLog.onRename((IdentifiedUser) userProvider.get(), oldProjectKey, input, ex);
    }
  }

  void fsRenameStep(
      Project.NameKey oldProjectKey, Project.NameKey newProjectKey, Optional<ProgressMonitor> pm)
      throws IOException {
    fsHandler.rename(oldProjectKey, newProjectKey, pm);
    logPerformedStep(Step.FILESYSTEM, newProjectKey, oldProjectKey);
  }

  void cacheRenameStep(Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws IOException {
    cacheHandler.update(oldProjectKey, newProjectKey);
    logPerformedStep(Step.CACHE, newProjectKey, oldProjectKey);
  }

  List<Change.Id> dbRenameStep(
      List<Change.Id> changeIds,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      Optional<ProgressMonitor> pm)
      throws IOException, ConfigInvalidException, RenameRevertException {
    List<Change.Id> updatedChangeIds = dbHandler.rename(changeIds, newProjectKey, pm);
    logPerformedStep(Step.DATABASE, newProjectKey, oldProjectKey);
    return updatedChangeIds;
  }

  void indexRenameStep(
      List<Change.Id> updatedChangeIds,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      Optional<ProgressMonitor> pm)
      throws InterruptedException {
    indexHandler.updateIndex(updatedChangeIds, newProjectKey, pm);
    logPerformedStep(Step.INDEX, newProjectKey, oldProjectKey);
  }

  enum Step {
    FILESYSTEM,
    CACHE,
    DATABASE,
    INDEX
  }

  private void logPerformedStep(
      Step step, Project.NameKey newProjectKey, Project.NameKey oldProjectKey) {
    stepsPerformed.add(step);
    switch (step) {
      case FILESYSTEM:
        log.debug("Renamed the git repo to {} successfully.", newProjectKey.get());
        break;
      case CACHE:
        log.debug("Successfully updated project cache for project {}.", newProjectKey.get());
        break;
      case DATABASE:
        log.debug("Updated the changes in DB successfully for project {}.", oldProjectKey.get());
        break;
      case INDEX:
        log.debug("Updated the secondary index successfully for project {}.", oldProjectKey.get());
    }
  }

  @VisibleForTesting
  List<Step> getStepsPerformed() {
    return stepsPerformed;
  }

  List<Change.Id> getChanges(ProjectResource rsrc, Optional<ProgressMonitor> pm)
      throws IOException {
    pm.ifPresent(
        progressMonitor -> progressMonitor.beginTask("Retrieving the list of changes from DB"));
    Project.NameKey oldProjectKey = rsrc.getNameKey();
    return dbHandler.getChangeIds(oldProjectKey);
  }
}

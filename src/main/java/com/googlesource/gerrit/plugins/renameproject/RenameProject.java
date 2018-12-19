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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.extensions.events.PluginEvent;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.conditions.RenamePreconditions;
import com.googlesource.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.googlesource.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
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
      RenameLog renameLog) {
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
    CapabilityControl ctl = userProvider.get().getCapabilities();
    return ctl.canAdministrateServer()
        || ctl.canPerform(pluginName + "-" + RENAME_PROJECT)
        || (ctl.canPerform(pluginName + "-" + RENAME_OWN_PROJECT) && rsrc.getControl().isOwner());
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
    try {
      fsHandler.rename(oldProjectKey, newProjectKey, pm);
      log.debug("Renamed the git repo to {} successfully.", newProjectKey.get());
      cacheHandler.update(rsrc.getControl().getProject(), newProjectKey);

      List<Change.Id> updatedChangeIds =
          dbHandler.rename(changeIds, oldProjectKey, newProjectKey, pm);
      log.debug("Updated the changes in DB successfully for project {}.", oldProjectKey.get());

      // if the DB update is successful, update the secondary index
      indexHandler.updateIndex(updatedChangeIds, newProjectKey, pm);
      log.debug("Updated the secondary index successfully for project {}.", oldProjectKey.get());

      lockUnlockProject.unlock(newProjectKey);
      log.debug("Unlocked the repo {} after rename operation.", newProjectKey.get());

      pluginEvent.fire(pluginName, pluginName, oldProjectKey.get() + ":" + newProjectKey.get());
    } catch (Exception e) {
      ex = e;
      throw e;
    } finally {
      renameLog.onRename((IdentifiedUser) userProvider.get(), oldProjectKey, input, ex);
    }
  }

  List<Change.Id> getChanges(ProjectResource rsrc, ProgressMonitor pm) throws OrmException {
    pm.beginTask("Retrieving the list of changes from DB");
    Project.NameKey oldProjectKey = rsrc.getControl().getProject().getNameKey();
    return dbHandler.getChangeIds(oldProjectKey);
  }
}

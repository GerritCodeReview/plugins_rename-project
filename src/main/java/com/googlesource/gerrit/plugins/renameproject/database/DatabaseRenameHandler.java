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

package com.googlesource.gerrit.plugins.renameproject.database;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectWatchKey;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.RenameRevertException;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DatabaseRenameHandler {
  private static final Logger log = LoggerFactory.getLogger(DatabaseRenameHandler.class);

  private final GitRepositoryManager repoManager;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  public DatabaseRenameHandler(
      GitRepositoryManager repoManager,
      Provider<InternalAccountQuery> accountQueryProvider,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.accountQueryProvider = accountQueryProvider;
    this.repoManager = repoManager;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  public Set<Change.Id> getChangeIds(Project.NameKey oldProjectKey) throws IOException {
    log.debug("Starting to retrieve changes from the DB for project {}", oldProjectKey.get());
    Set<Change.Id> changeIds;
    try (Repository repo = repoManager.openRepository(oldProjectKey)) {
      changeIds = ChangeNotes.Factory.scanChangeIds(repo).keySet();
    }
    log.debug(
        "Number of changes in noteDb related to project {} are {}",
        oldProjectKey.get(),
        changeIds.size());
    return changeIds;
  }

  public void updateWatchEntriesWithRollback(
      Project.NameKey oldProjectKey, Project.NameKey newProjectKey, ProgressMonitor pm)
      throws RenameRevertException, IOException, ConfigInvalidException {
    pm.beginTask("Updating project watch entries");
    log.debug(
        "Updating watch entries from project {} to project {}",
        oldProjectKey.get(),
        newProjectKey.get());
    try {
      updateWatchEntries(oldProjectKey, newProjectKey);
    } catch (Exception e) {
      log.error(
          "Failed to update watch entries for project {}, exception caught: {}. Rolling back the"
              + " operation.",
          oldProjectKey.get(),
          e.toString());
      try {
        updateWatchEntries(newProjectKey, oldProjectKey);
      } catch (Exception revertEx) {
        log.error(
            "Failed to rollback changes in noteDb from project {} to project {}, exception caught:"
                + " {}",
            newProjectKey.get(),
            oldProjectKey.get(),
            revertEx.toString());
        throw new RenameRevertException(revertEx, e);
      }
      throw e;
    }
    log.debug(
        "Successfully updated watch entries from project {} to project {}",
        oldProjectKey.get(),
        newProjectKey.get());
  }

  public void updateWatchEntries(Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws IOException, ConfigInvalidException {
    for (AccountState a : accountQueryProvider.get().byWatchedProject(oldProjectKey)) {
      Account.Id accountId = a.account().id();
      ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches = a.projectWatches();
      Map<ProjectWatchKey, Set<NotifyType>> newProjectWatches = new HashMap<>();
      List<ProjectWatchKey> oldProjectWatches = new ArrayList<>();
      for (ProjectWatchKey watchKey : a.projectWatches().keySet()) {
        if (oldProjectKey.equals(watchKey.project())) {
          newProjectWatches.put(
              ProjectWatchKey.create(newProjectKey, watchKey.filter()),
              projectWatches.get(watchKey));
          oldProjectWatches.add(watchKey);
          try {
            accountsUpdateProvider
                .get()
                .update(
                    "Add watch entry",
                    accountId,
                    (accountState, update) ->
                        update.updateProjectWatches(newProjectWatches).build());
            accountsUpdateProvider
                .get()
                .update(
                    "Remove watch entry",
                    accountId,
                    (accountState, update) ->
                        update.deleteProjectWatches(oldProjectWatches).build());
          } catch (ConfigInvalidException e) {
            log.error(
                "Updating watch entry for user {} in project {} failed. Watch config found"
                    + " invalid.",
                a.userName(),
                newProjectKey.get(),
                e);
            throw e;
          } catch (IOException e) {
            log.error(
                "Updating watch entry for user {} in project {} failed.",
                a.userName(),
                newProjectKey.get(),
                e);
            throw e;
          }
        }
      }
    }
  }
}

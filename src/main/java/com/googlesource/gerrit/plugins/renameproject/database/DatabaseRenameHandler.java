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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DatabaseRenameHandler {
  private static final Logger log = LoggerFactory.getLogger(DatabaseRenameHandler.class);

  private final ChangeNotes.Factory schemaFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final GitRepositoryManager repoManager;
  private final Provider<CurrentUser> userProvider;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final Map<Change.Id, ChangeNotes> changeNotes = new HashMap<>();

  private Project.NameKey oldProjectKey;

  @Inject
  public DatabaseRenameHandler(
      ChangeNotes.Factory schemaFactory,
      ChangeUpdate.Factory updateFactory,
      GitRepositoryManager repoManager,
      Provider<CurrentUser> userProvider,
      Provider<InternalAccountQuery> accountQueryProvider,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.accountQueryProvider = accountQueryProvider;
    this.schemaFactory = schemaFactory;
    this.updateFactory = updateFactory;
    this.repoManager = repoManager;
    this.userProvider = userProvider;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  public List<Change.Id> getChangeIds(Project.NameKey oldProjectKey) throws IOException {
    log.debug("Starting to retrieve changes from the DB for project {}", oldProjectKey.get());
    this.oldProjectKey = oldProjectKey;

    List<Change.Id> changeIds = new ArrayList<>();
    Stream<ChangeNotesResult> changes =
        schemaFactory.scan(repoManager.openRepository(oldProjectKey), oldProjectKey);
    Iterator<ChangeNotesResult> iterator = changes.iterator();
    while (iterator.hasNext()) {
      ChangeNotesResult change = iterator.next();
      Change.Id changeId = change.id();
      changeIds.add(changeId);
      changeNotes.put(changeId, change.notes());
    }
    log.debug(
        "Number of changes related to the project {} are {}",
        oldProjectKey.get(),
        changeIds.size());
    return changeIds;
  }

  public List<Change.Id> rename(
      List<Change.Id> changes, Project.NameKey newProjectKey, ProgressMonitor pm)
      throws IOException {
    pm.beginTask("Updating changes in the database");
    log.debug("Updating the changes in the DB related to project {}", oldProjectKey.get());
    List<ChangeUpdate> updates = getChangeUpdates(changes, newProjectKey);
    updateWatchEntries(newProjectKey);
    List<Change> updated = new ArrayList<>();
    try {
      for (ChangeUpdate update : updates) {
        update.commit();
        updated.add(update.getChange());
      }
    } catch (IOException e) {
      // TODO(mmiller): Consider covering this path with tests, albeit exceptional.
      log.error(
          "Failed to update changes in the DB for the project {}, rolling back the operation.",
          oldProjectKey.get());
      rollback(updated, newProjectKey);
      throw e;
    }
    log.debug(
        "Successfully updated the changes in the DB related to project {}", oldProjectKey.get());
    return changes;
  }

  private List<ChangeUpdate> getChangeUpdates(List<Change.Id> changeIds, Project.NameKey nameKey) {
    List<ChangeUpdate> updates = new ArrayList<>();
    Date from = Date.from(Instant.now());
    changeIds.forEach(
        changeId -> {
          ChangeNotes notes = schemaFactory.create(nameKey, changeId);
          ChangeUpdate update = updateFactory.create(notes, userProvider.get(), from);
          updates.add(update);
        });
    return updates;
  }

  private void rollback(List<Change> changes, Project.NameKey newProjectKey) {
    Date from = Date.from(Instant.now());
    changes.forEach(
        change -> {
          ChangeNotes notes = changeNotes.get(change.getId());
          ChangeUpdate revert = updateFactory.create(notes, userProvider.get(), from);
          revert.setRevertOf(change.getChangeId());
          try {
            revert.commit();
          } catch (IOException ex) {
            log.error(
                "Failed to rollback change {} in DB from project {} to {}; rolling back others still.",
                change.getChangeId(),
                newProjectKey.get(),
                oldProjectKey.get());
          }
        });
  }

  private void updateWatchEntries(Project.NameKey newProjectKey) {
    for (AccountState a : accountQueryProvider.get().byWatchedProject(oldProjectKey)) {
      Account.Id accountId = a.getAccount().getId();

      ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches =
          a.getProjectWatches();

      Map<ProjectWatchKey, Set<NotifyType>> newProjectWatches = new HashMap<>();
      List<ProjectWatchKey> toBeRemovedProjectWatches = new ArrayList<>();
      for (ProjectWatchKey watchKey : a.getProjectWatches().keySet()) {
        if (oldProjectKey.equals(watchKey.project())) {
          newProjectWatches.put(
              ProjectWatchKey.create(newProjectKey, watchKey.filter()),
              projectWatches.get(watchKey));
          toBeRemovedProjectWatches.add(watchKey);
          try {
            accountsUpdateProvider
                .get()
                .update(
                    "Update watch entry",
                    accountId,
                    (accountState, update) ->
                        update.updateProjectWatches(newProjectWatches).build());

            accountsUpdateProvider
                .get()
                .update(
                    "Remove watch entry",
                    accountId,
                    (accountState, update) ->
                        update.deleteProjectWatches(toBeRemovedProjectWatches).build());

          } catch (ConfigInvalidException e) {
            log.error(
                "Updating watch entry for user {} in project {} failed. Watch config found invalid.",
                a.getUserName(),
                newProjectKey.get(),
                e);
          } catch (IOException e) {
            log.error(
                "Updating watch entry for user {} in project {} failed.",
                a.getUserName(),
                newProjectKey.get(),
                e);
          }
        }
      }
    }
  }
}

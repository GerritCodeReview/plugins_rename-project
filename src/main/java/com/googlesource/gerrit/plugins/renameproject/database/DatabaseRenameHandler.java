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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.DisallowReadFromChangesReviewDbWrapper;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.account.WatchConfig.Accessor;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<Accessor> watchConfig;

  private final ChangeNotes.Factory schemaFactoryNoteDB;
  private final GitRepositoryManager repoManager;
  private final ChangeUpdate.Factory updateFactory;
  private final Provider<CurrentUser> userProvider;
  private final Map<Id, ChangeNotes> changeNotes = new HashMap<>();

  private Project.NameKey oldProjectKey;

  @Inject
  public DatabaseRenameHandler(
      ChangeNotes.Factory schemaFactoryNoteDB,
      ChangeUpdate.Factory updateFactory,
      GitRepositoryManager repoManager,
      Provider<CurrentUser> userProvider,
      SchemaFactory<ReviewDb> schemaFactory,
      Provider<InternalAccountQuery> accountQueryProvider,
      Provider<WatchConfig.Accessor> watchConfig) {
    this.schemaFactoryNoteDB = schemaFactoryNoteDB;
    this.updateFactory = updateFactory;
    this.repoManager = repoManager;
    this.userProvider = userProvider;
    this.accountQueryProvider = accountQueryProvider;
    this.watchConfig = watchConfig;
    this.schemaFactory = schemaFactory;
  }

  public List<Change.Id> getChangeIds(Project.NameKey oldProjectKey)
      throws OrmException, IOException {
    log.debug("Starting to retrieve changes from the DB for project {}", oldProjectKey.get());
    List<Change.Id> changeIds = new ArrayList<>();
    ReviewDb openDB = schemaFactory.open();
    this.oldProjectKey = oldProjectKey;
    Stream<ChangeNotesResult> changes =
        schemaFactoryNoteDB.scan(repoManager.openRepository(oldProjectKey), openDB, oldProjectKey);
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
      List<Change.Id> changes,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      ProgressMonitor pm)
      throws OrmException, IOException {
    pm.beginTask("Updating changes in the database");

    return (schemaFactory.open() instanceof DisallowReadFromChangesReviewDbWrapper)
        ? renameInNoteDB(changes, oldProjectKey, newProjectKey)
        : renameInReviewDB(changes, oldProjectKey, newProjectKey);
  }

  private List<Change.Id> renameInReviewDB(
      List<Change.Id> changes, Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws OrmException {
    Connection conn = ((JdbcSchema) schemaFactory.open()).getConnection();
    try (Statement stmt = conn.createStatement()) {
      conn.setAutoCommit(false);
      try {
        log.debug("Updating the changes in the DB related to project {}", oldProjectKey.get());
        for (Change.Id cd : changes) {
          stmt.addBatch(
              "update changes set dest_project_name='"
                  + newProjectKey.get()
                  + "' where change_id ="
                  + cd.id
                  + ";");
        }
        stmt.executeBatch();
        updateWatchEntries(oldProjectKey, newProjectKey);
        conn.commit();
        log.debug(
            "Successfully updated the changes in the DB related to project {}",
            oldProjectKey.get());
        return changes;
      } finally {
        conn.setAutoCommit(true);
      }

    } catch (SQLException e) {

      try {

        log.error(
            "Failed to update changes in the DB for the project {}, rolling back the operation.",
            oldProjectKey.get());

        conn.rollback();

      } catch (SQLException ex) {

        throw new OrmException(ex);
      }

      throw new OrmException(e);
    }
  }

  private List<Change.Id> renameInNoteDB(
      List<Change.Id> changes, Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws OrmException, IOException {

    log.debug("Updating the changes in the DB related to project {}", oldProjectKey.get());

    List<ChangeUpdate> updates = getChangeUpdates(changes, newProjectKey, schemaFactory.open());
    List<Change> updated = new ArrayList<>();
    try {
      for (ChangeUpdate update : updates) {
        update.commit();
        updated.add(update.getChange());
      }
      updateWatchEntries(oldProjectKey, newProjectKey);
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

  private void rollback(List<Change> changes, Project.NameKey newProjectKey) {
    Date from = Date.from(Instant.now());
    changes.forEach(
        change -> {
          ChangeNotes notes = changeNotes.get(change.getId());
          ChangeUpdate revert = updateFactory.create(notes, userProvider.get(), from);
          revert.setRevertOf(change.getChangeId());
          try {
            revert.commit();
          } catch (IOException | OrmException ex) {
            log.error(
                "Failed to rollback change {} in DB from project {} to {}; rolling back others still.",
                change.getChangeId(),
                newProjectKey.get(),
                oldProjectKey.get());
          }
        });
  }

  private List<ChangeUpdate> getChangeUpdates(
      List<Change.Id> changeIds, Project.NameKey nameKey, ReviewDb db) {
    List<ChangeUpdate> updates = new ArrayList<>();
    Date from = Date.from(Instant.now());
    changeIds.forEach(
        changeId -> {
          ChangeNotes notes = null;
          try {
            notes = schemaFactoryNoteDB.create(db, nameKey, changeId);
          } catch (OrmException e) {
            e.printStackTrace();
          }
          ChangeUpdate update = updateFactory.create(notes, userProvider.get(), from);
          updates.add(update);
        });
    return updates;
  }

  private void updateWatchEntries(Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws OrmException {
    for (AccountState a : accountQueryProvider.get().byWatchedProject(oldProjectKey)) {
      Account.Id accountId = a.getAccount().getId();
      for (ProjectWatchKey watchKey : a.getProjectWatches().keySet()) {
        if (oldProjectKey.equals(watchKey.project())) {
          try {
            Map<ProjectWatchKey, Set<NotifyType>> newProjectWatches =
                watchConfig.get().getProjectWatches(accountId);

            newProjectWatches.put(
                ProjectWatchKey.create(newProjectKey, watchKey.filter()),
                a.getProjectWatches().get(watchKey));

            newProjectWatches.remove(watchKey);

            watchConfig.get().deleteAllProjectWatches(accountId);
            watchConfig.get().upsertProjectWatches(accountId, newProjectWatches);
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

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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.account.WatchConfig.Accessor;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.RenameRevertException;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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
  private final ChangeNotes.Factory schemaFactoryNoteDb;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<Accessor> watchConfig;
  private NotesMigration migration;

  @Inject
  public DatabaseRenameHandler(
      SchemaFactory<ReviewDb> schemaFactory,
      ChangeNotes.Factory schemaFactoryNoteDb,
      GitRepositoryManager repoManager,
      NotesMigration migration,
      Provider<InternalAccountQuery> accountQueryProvider,
      Provider<WatchConfig.Accessor> watchConfig) {
    this.accountQueryProvider = accountQueryProvider;
    this.watchConfig = watchConfig;
    this.schemaFactory = schemaFactory;
    this.schemaFactoryNoteDb = schemaFactoryNoteDb;
    this.repoManager = repoManager;
    this.migration = migration;
  }

  public List<Change.Id> getChangeIds(Project.NameKey oldProjectKey)
      throws OrmException, IOException {
    log.debug("Starting to retrieve changes from the DB for project {}", oldProjectKey.get());
    ReviewDb db = schemaFactory.open();
    return (isNoteDb())
        ? getChangeIdsFromNoteDb(oldProjectKey, db)
        : getChangeIdsFromReviewDb(oldProjectKey, db);
  }

  private List<Change.Id> getChangeIdsFromReviewDb(Project.NameKey oldProjectKey, ReviewDb db)
      throws OrmException {
    List<Change.Id> changeIds = new ArrayList<>();
    Connection conn = ((JdbcSchema) db).getConnection();
    String query =
        "select change_id from changes where dest_project_name ='" + oldProjectKey.get() + "';";
    try (Statement stmt = conn.createStatement();
        ResultSet changes = stmt.executeQuery(query)) {
      while (changes != null && changes.next()) {
        Change.Id changeId = new Change.Id(changes.getInt(1));
        changeIds.add(changeId);
      }
      log.debug(
          "Number of changes in reviewDb related to project {} are {}",
          oldProjectKey.get(),
          changeIds.size());
      return changeIds;
    } catch (SQLException e) {
      throw new OrmException(e);
    }
  }

  private List<Change.Id> getChangeIdsFromNoteDb(Project.NameKey oldProjectKey, ReviewDb db)
      throws IOException {
    List<Change.Id> changeIds = new ArrayList<>();
    Stream<ChangeNotesResult> changes =
        schemaFactoryNoteDb.scan(repoManager.openRepository(oldProjectKey), db, oldProjectKey);
    Iterator<ChangeNotesResult> iterator = changes.iterator();
    while (iterator.hasNext()) {
      ChangeNotesResult change = iterator.next();
      changeIds.add(change.id());
    }
    log.debug(
        "Number of changes in noteDb related to project {} are {}",
        oldProjectKey.get(),
        changeIds.size());
    return changeIds;
  }

  private boolean isNoteDb() {
    return migration.disableChangeReviewDb();
  }

  public List<Change.Id> rename(
      List<Change.Id> changes,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      ProgressMonitor pm)
      throws OrmException, RenameRevertException {
    pm.beginTask("Updating changes in the database");
    ReviewDb db = schemaFactory.open();
    return (isNoteDb())
        ? renameInNoteDb(changes, oldProjectKey, newProjectKey)
        : renameInReviewDb(changes, oldProjectKey, newProjectKey, db);
  }

  private List<Change.Id> renameInReviewDb(
      List<Change.Id> changes,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      ReviewDb db)
      throws OrmException {
    Connection conn = ((JdbcSchema) db).getConnection();
    try (Statement stmt = conn.createStatement()) {
      conn.setAutoCommit(false);
      try {
        try {
          log.debug("Updating the changes in reviewDb related to project {}", oldProjectKey.get());
          for (Change.Id cd : changes) {
            stmt.addBatch(
                "update changes set dest_project_name='"
                    + newProjectKey.get()
                    + "' where change_id ="
                    + cd.id
                    + ";");
          }
          stmt.executeBatch();
          conn.commit();
        } catch (SQLException e) {
          throw new OrmException(e);
        }
        updateWatchEntries(oldProjectKey, newProjectKey);
        log.debug(
            "Successfully updated the changes in reviewDb related to project {}",
            oldProjectKey.get());
        return changes;
      } catch (OrmException e) {
        try {
          log.error(
              "Failed to update changes in reviewDb for project {}, exception caught: {}. Rolling back the operation.",
              oldProjectKey.get(),
              e.toString());
          conn.rollback();
        } catch (SQLException revertEx) {
          log.error(
              "Failed to rollback changes in reviewDb from project {} to project {}, exception caught: {}",
              newProjectKey.get(),
              oldProjectKey.get(),
              revertEx.toString());
          throw new RenameRevertException(revertEx, e);
        }
        try {
          updateWatchEntries(newProjectKey, oldProjectKey);
        } catch (OrmException revertEx) {
          log.error(
              "Failed to update watched changes in reviewDb from project {} to project {}, exception caught: {}",
              newProjectKey.get(),
              oldProjectKey.get(),
              revertEx.toString());
          throw new RenameRevertException(revertEx, e);
        }
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new OrmException(e);
    }
  }

  private List<Change.Id> renameInNoteDb(
      List<Change.Id> changes, Project.NameKey oldProjectKey, Project.NameKey newProjectKey)
      throws OrmException {
    log.debug("Updating the changes in noteDb related to project {}", oldProjectKey.get());
    try {
      updateWatchEntries(oldProjectKey, newProjectKey);
    } catch (OrmException e) {
      log.error(
          "Failed to update changes in noteDb for project {}, exception caught: {}. Rolling back the operation.",
          oldProjectKey.get(),
          e.toString());
      try {
        updateWatchEntries(newProjectKey, oldProjectKey);
      } catch (OrmException revertEx) {
        log.error(
            "Failed to rollback changes in noteDb from project {} to project {}, exception caught: {}",
            newProjectKey.get(),
            oldProjectKey.get(),
            revertEx.toString());
        throw new RenameRevertException(revertEx, e);
      }
      throw e;
    }

    log.debug(
        "Successfully updated the changes in noteDb related to project {}", oldProjectKey.get());
    return changes;
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
            throw new OrmException(e);
          } catch (IOException e) {
            log.error(
                "Updating watch entry for user {} in project {} failed.",
                a.getUserName(),
                newProjectKey.get(),
                e);
            throw new OrmException(e);
          }
        }
      }
    }
  }
}

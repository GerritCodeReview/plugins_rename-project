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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DatabaseRenameHandler {
  private static final Logger log = LoggerFactory.getLogger(DatabaseRenameHandler.class);

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Provider<Accessor> watchConfig;

  @Inject
  public DatabaseRenameHandler(
      SchemaFactory<ReviewDb> schemaFactory,
      Provider<InternalAccountQuery> accountQueryProvider,
      Provider<WatchConfig.Accessor> watchConfig) {
    this.accountQueryProvider = accountQueryProvider;
    this.watchConfig = watchConfig;
    this.schemaFactory = schemaFactory;
  }

  public List<Change.Id> getChangeIds(Project.NameKey oldProjectKey) throws OrmException {
    log.debug("Starting to retrieve changes from the DB for project {}", oldProjectKey.get());

    List<Change.Id> changeIds = new ArrayList<>();
    Connection conn = ((JdbcSchema) schemaFactory.open()).getConnection();
    String query =
        "select change_id from changes where dest_project_name ='" + oldProjectKey.get() + "';";
    try (Statement stmt = conn.createStatement();
        ResultSet changes = stmt.executeQuery(query)) {
      while (changes != null && changes.next()) {
        Change.Id changeId = new Change.Id(changes.getInt(1));
        changeIds.add(changeId);
      }
      log.debug(
          "Number of changes related to the project {} are {}",
          oldProjectKey.get(),
          changeIds.size());
      return changeIds;
    } catch (SQLException e) {
      throw new OrmException(e);
    }
  }

  public List<Change.Id> rename(
      List<Change.Id> changes,
      Project.NameKey oldProjectKey,
      Project.NameKey newProjectKey,
      ProgressMonitor pm)
      throws OrmException {
    pm.beginTask("Updating changes in the database");
    ReviewDb db = schemaFactory.open();
    return renameInReviewDb(changes, oldProjectKey, newProjectKey, db);
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

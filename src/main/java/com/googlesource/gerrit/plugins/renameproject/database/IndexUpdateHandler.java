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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.Configuration;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IndexUpdateHandler {
  private static final Logger log = LoggerFactory.getLogger(IndexUpdateHandler.class);

  private final ChangeIndexer indexer;
  private final Configuration config;

  @Inject
  public IndexUpdateHandler(ChangeIndexer indexer, Configuration config) {
    this.indexer = indexer;
    this.config = config;
  }

  public void updateIndex(
      List<Change.Id> changeIds, Project.NameKey newProjectKey, ProgressMonitor pm)
      throws InterruptedException {
    log.debug("Starting to index {} change(s).", changeIds.size());
    ExecutorService executor =
        Executors.newFixedThreadPool(
            config.getIndexThreads(),
            new ThreadFactoryBuilder().setNameFormat("Rename-Index-%d").build());
    pm.beginTask("Indexing changes", changeIds.size());
    List<Callable<Boolean>> callableTasks = new ArrayList<>(changeIds.size());
    for (Change.Id id : changeIds) {
      callableTasks.add(new IndexTask(id, newProjectKey, pm));
    }
    List<Future<Boolean>> tasksCompleted = executor.invokeAll(callableTasks);
    executor.shutdown();

    if (verifyAllTasksCompleted(tasksCompleted)) {
      log.debug("Indexed {} change(s) successfully.", changeIds.size());
    }
  }

  private boolean verifyAllTasksCompleted(List<Future<Boolean>> executorOutput) {
    return executorOutput.stream()
        .allMatch(
            task -> {
              try {
                return task.get();
              } catch (InterruptedException | ExecutionException e) {
                log.error("Could not check if the task was completed.", e);
              }
              return false;
            });
  }

  private class IndexTask implements Callable<Boolean> {

    private Change.Id changeId;
    private Project.NameKey newProjectKey;
    private ProgressMonitor monitor;

    IndexTask(Change.Id changeId, Project.NameKey newProjectKey, ProgressMonitor monitor) {
      this.changeId = changeId;
      this.newProjectKey = newProjectKey;
      this.monitor = monitor;
    }

    @Override
    public Boolean call() throws Exception {
      indexer.index(newProjectKey, changeId);
      monitor.update(1);
      return Boolean.TRUE;
    }
  }
}

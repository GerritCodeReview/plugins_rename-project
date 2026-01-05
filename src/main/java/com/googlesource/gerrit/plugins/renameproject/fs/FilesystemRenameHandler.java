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

package com.googlesource.gerrit.plugins.renameproject.fs;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FilesystemRenameHandler {
  private static final Logger log = LoggerFactory.getLogger(FilesystemRenameHandler.class);

  private final GitRepositoryManager repoManager;

  @Inject
  public FilesystemRenameHandler(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  public void rename(
      Project.NameKey oldProjectKey, Project.NameKey newProjectKey, ProgressMonitor pm)
      throws IOException, RepositoryNotFoundException {
    try (Repository repository = repoManager.openRepository(oldProjectKey)) {
      File repoFile = repository.getDirectory();
      RepositoryCache.close(repository);
      pm.beginTask("Renaming git repository");
      renameGitRepository(repoFile, newProjectKey, oldProjectKey);
    }
  }

  private void renameGitRepository(
      File source, Project.NameKey newProjectKey, Project.NameKey oldProjectKey)
      throws IOException {
    log.debug("Creating the new git repo - {}", newProjectKey.get());
    try (Repository newRepo = repoManager.createRepository(newProjectKey)) {
      File target = newRepo.getDirectory();
      RepositoryCache.close(newRepo);
      // delete the created repo, we just needed the absolute path from repo manager
      recursiveDelete(target.toPath());
      log.debug(
          "Moving the content of {} to new git repo - {}",
          oldProjectKey.get(),
          newProjectKey.get());
      Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new IOException("Failed to move the content to new git repo.", e);
    }
  }

  private void recursiveDelete(Path oldFile) throws IOException {
    try (Stream<Path> dir = Files.walk(oldFile, FileVisitOption.FOLLOW_LINKS)) {
      dir.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    } catch (IOException e) {
      log.error("Failed to delete {}", oldFile.getFileName(), e);
      throw e;
    }
  }
}

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

package com.ericsson.gerrit.plugins.renameproject.conditions;

import com.ericsson.gerrit.plugins.renameproject.CannotRenameProjectException;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOpRepoManager;
import com.google.gerrit.server.git.SubmoduleOp;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks all the preconditions before renaming project. */
@Singleton
public class RenamePreconditions {

  private static final Logger log = LoggerFactory.getLogger(RenamePreconditions.class);

  public final AllProjectsName allProjectsName;
  public final AllUsersName allUsersName;
  private final Provider<ListChildProjects> listChildProjectsProvider;
  private final GitRepositoryManager repoManager;
  private final SubmoduleOp.Factory subOpFactory;
  private final Provider<MergeOpRepoManager> ormProvider;

  @Inject
  public RenamePreconditions(
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      Provider<ListChildProjects> listChildProjectsProvider,
      GitRepositoryManager repoManager,
      SubmoduleOp.Factory subOpFactory,
      Provider<MergeOpRepoManager> ormProvider) {
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.listChildProjectsProvider = listChildProjectsProvider;
    this.repoManager = repoManager;
    this.subOpFactory = subOpFactory;
    this.ormProvider = ormProvider;
  }

  public void assertCanRename(ProjectResource oldProjectRsrc, Project.NameKey newProjectKey)
      throws CannotRenameProjectException {
    Project.NameKey oldProjectKey = oldProjectRsrc.getControl().getProject().getNameKey();
    assertNewRepoNotExists(newProjectKey);
    assertIsNotDefaultProject(oldProjectKey);
    assertHasNoChildProjects(oldProjectRsrc);
    assertIsNotSubscribed(oldProjectKey);
  }

  private void assertIsNotDefaultProject(Project.NameKey key) throws CannotRenameProjectException {
    if (key.equals(allProjectsName) || key.equals(allUsersName)) {
      String message = String.format("Cannot rename the '%s' project", key);
      log.error(message);
      throw new CannotRenameProjectException(message);
    }
  }

  private void assertHasNoChildProjects(ProjectResource rsrc) throws CannotRenameProjectException {
    List<ProjectInfo> children = listChildProjectsProvider.get().apply(rsrc);
    if (!children.isEmpty()) {
      String childrenString =
          String.join(", ", children.stream().map(info -> info.name).collect(Collectors.toList()));
      String message =
          String.format("Cannot rename project because it has children: %s", childrenString);
      log.error(message);
      throw new CannotRenameProjectException(message);
    }
  }

  private void assertIsNotSubscribed(Project.NameKey key) throws CannotRenameProjectException {
    try (Repository repo = repoManager.openRepository(key);
        MergeOpRepoManager orm = ormProvider.get()) {
      Set<Branch.NameKey> branches = new HashSet<>();
      for (Ref ref : repo.getRefDatabase().getRefs(RefNames.REFS_HEADS).values()) {
        branches.add(new Branch.NameKey(key, ref.getName()));
      }
      SubmoduleOp sub = subOpFactory.create(branches, orm);
      for (Branch.NameKey b : branches) {
        if (!sub.superProjectSubscriptionsForSubmoduleBranch(b).isEmpty()) {
          String message = "Cannot rename a project subscribed to by the other projects";
          log.error(message);
          throw new CannotRenameProjectException(message);
        }
      }
    } catch (IOException e) {
      throw new CannotRenameProjectException(e);
    }
  }

  private void assertNewRepoNotExists(Project.NameKey key) throws CannotRenameProjectException {
    try (Repository repo = repoManager.openRepository(key)) {
      if (repo.getObjectDatabase().exists()) {
        throw new CannotRenameProjectException(
            "A project with this name already exists, choose a different name");
      }
    } catch (RepositoryNotFoundException ignored) {
      // Repo does not exist, safe to ignore the exception
    } catch (IOException e) {
      throw new CannotRenameProjectException(e);
    }
  }
}

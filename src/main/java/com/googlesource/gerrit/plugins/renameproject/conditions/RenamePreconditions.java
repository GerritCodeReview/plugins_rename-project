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

package com.googlesource.gerrit.plugins.renameproject.conditions;

import static com.google.gerrit.entities.RefNames.REFS_HEADS;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ListChildProjects;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleConflictException;
import com.google.gerrit.server.submit.SubscriptionGraph;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.renameproject.CannotRenameProjectException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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
  private final SubscriptionGraph.Factory subscriptionGraphFactory;
  private final Provider<MergeOpRepoManager> ormProvider;

  @Inject
  public RenamePreconditions(
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      Provider<ListChildProjects> listChildProjectsProvider,
      GitRepositoryManager repoManager,
      SubscriptionGraph.Factory subscriptionGraphFactory,
      Provider<MergeOpRepoManager> ormProvider) {
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.listChildProjectsProvider = listChildProjectsProvider;
    this.repoManager = repoManager;
    this.subscriptionGraphFactory = subscriptionGraphFactory;
    this.ormProvider = ormProvider;
  }

  public void assertCanRename(ProjectResource oldProjectRsrc, Project.NameKey newProjectKey)
      throws CannotRenameProjectException {
    Project.NameKey oldProjectKey = oldProjectRsrc.getNameKey();
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
    try {
      Response<List<ProjectInfo>> children = listChildProjectsProvider.get().apply(rsrc);
      if (!children.value().isEmpty()) {
        String childrenString =
            String.join(
                ", ",
                children.value().stream().map(info -> info.name).collect(Collectors.toList()));
        String message =
            String.format("Cannot rename project because it has children: %s", childrenString);
        log.error(message);
        throw new CannotRenameProjectException(message);
      }
    } catch (Exception e) {
      throw new CannotRenameProjectException(e);
    }
  }

  private void assertIsNotSubscribed(Project.NameKey key) throws CannotRenameProjectException {
    try (Repository repo = repoManager.openRepository(key);
        MergeOpRepoManager orm = ormProvider.get()) {
      Set<BranchNameKey> branches =
          repo.getRefDatabase().getRefsByPrefix(REFS_HEADS).stream()
              .map(ref -> BranchNameKey.create(key, ref.getName()))
              .collect(toSet());
      SubscriptionGraph sub = subscriptionGraphFactory.compute(branches, orm);
      for (BranchNameKey b : branches) {
        if (sub.hasSuperproject(b)) {
          String message = "Cannot rename a project subscribed to by the other projects";
          log.error(message);
          throw new CannotRenameProjectException(message);
        }
      }
    } catch (IOException | SubmoduleConflictException e) {
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

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.ListChildProjects;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubscriptionGraph;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.renameproject.CannotRenameProjectException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RenamePreconditionsTest {

  @Mock private Provider<ListChildProjects> listChildProjectsProvider;
  @Mock private GitRepositoryManager repoManager;
  @Mock private Provider<MergeOpRepoManager> ormProvider;
  @Mock private RenamePreconditions preconditions;
  @Mock private ObjectDatabase objDb;
  @Mock private ProjectState control;
  @Mock private CurrentUser user;
  @Mock private Repository repo;
  @Mock private ListChildProjects listChildProjects;
  @Mock private SubscriptionGraph.Factory subscriptionGraphFactory;

  private AllProjectsName allProjects;
  private AllUsersName allUsersName;
  private ProjectResource oldRsrc;
  private Response<List<ProjectInfo>> children = Response.ok(new ArrayList<>());
  private Project.NameKey newProjectKey = Project.nameKey("newProject");

  @Before
  public void setUp() throws Exception {
    allProjects = new AllProjectsName(AllProjectsNameProvider.DEFAULT);
    allUsersName = new AllUsersName(AllUsersNameProvider.DEFAULT);
    oldRsrc = new ProjectResource(control, user);
    when(repoManager.openRepository(newProjectKey)).thenReturn(repo);
    when(repo.getObjectDatabase()).thenReturn(objDb);
    preconditions =
        new RenamePreconditions(
            allProjects,
            allUsersName,
            listChildProjectsProvider,
            repoManager,
            subscriptionGraphFactory,
            ormProvider);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameRepoExists() throws Exception {
    when(objDb.exists()).thenReturn(true);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameAllProjects() throws Exception {
    Project oldProject = Project.builder(allProjects).build();
    when(oldRsrc.getNameKey()).thenReturn(oldProject.getNameKey());
    when(objDb.exists()).thenReturn(false);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameAllUsers() throws Exception {
    Project oldProject = Project.builder(allUsersName).build();
    when(oldRsrc.getNameKey()).thenReturn(oldProject.getNameKey());
    when(objDb.exists()).thenReturn(false);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameHasChildren() throws Exception {
    Project oldProject = Project.builder(Project.nameKey("oldProject")).build();
    when(oldRsrc.getNameKey()).thenReturn(oldProject.getNameKey());
    when(objDb.exists()).thenReturn(false);

    when(listChildProjectsProvider.get()).thenReturn(listChildProjects);
    ProjectInfo projInfo = mock(ProjectInfo.class);
    children.value().add(projInfo);
    when(listChildProjects.apply(oldRsrc)).thenReturn(children);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }
}

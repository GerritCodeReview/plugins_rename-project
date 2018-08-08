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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.renameproject.CannotRenameProjectException;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOpRepoManager;
import com.google.gerrit.server.git.SubmoduleOp;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RenamePreconditionsTest {

  @Mock private AllProjectsName allProjects;
  @Mock private AllUsersName allUsersName;
  @Mock private Provider<ListChildProjects> listChildProjectsProvider;
  @Mock private GitRepositoryManager repoManager;
  @Mock private SubmoduleOp.Factory subOpFactory;
  @Mock private Provider<MergeOpRepoManager> ormProvider;
  @Mock private RenamePreconditions preconditions;
  @Mock private ObjectDatabase objDb;
  @Mock private ProjectControl control;
  @Mock private Repository repo;
  @Mock private ListChildProjects listChildProjects;

  private ProjectResource oldRsrc;
  private List<ProjectInfo> children = new ArrayList<>();
  private Project.NameKey newProjectKey = new Project.NameKey("newProject");

  @Before
  public void setUp() throws RepositoryNotFoundException, IOException {
    oldRsrc = new ProjectResource(control);
    when(repoManager.openRepository(newProjectKey)).thenReturn(repo);
    when(repo.getObjectDatabase()).thenReturn(objDb);
    preconditions =
        new RenamePreconditions(
            allProjects,
            allUsersName,
            listChildProjectsProvider,
            repoManager,
            subOpFactory,
            ormProvider);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameRepoExists()
      throws RepositoryNotFoundException, IOException, CannotRenameProjectException {
    Project oldProject = new Project(new Project.NameKey("oldProject"));
    when(control.getProject()).thenReturn(oldProject);
    when(objDb.exists()).thenReturn(true);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameAllProjects()
      throws RepositoryNotFoundException, IOException, CannotRenameProjectException {
    Project oldProject = new Project(allProjects);
    when(control.getProject()).thenReturn(oldProject);
    when(objDb.exists()).thenReturn(false);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameAllUsers()
      throws RepositoryNotFoundException, IOException, CannotRenameProjectException {
    Project oldProject = new Project(allUsersName);
    when(control.getProject()).thenReturn(oldProject);
    when(objDb.exists()).thenReturn(false);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }

  @Test(expected = CannotRenameProjectException.class)
  public void testAssertCannotRenameHasChildren()
      throws RepositoryNotFoundException, IOException, CannotRenameProjectException {
    Project oldProject = new Project(new Project.NameKey("oldProject"));
    when(control.getProject()).thenReturn(oldProject);
    when(objDb.exists()).thenReturn(false);

    when(listChildProjectsProvider.get()).thenReturn(listChildProjects);
    ProjectInfo projInfo = mock(ProjectInfo.class);
    children.add(projInfo);
    when(listChildProjects.apply(oldRsrc)).thenReturn(children);

    preconditions.assertCanRename(oldRsrc, newProjectKey);
  }
}

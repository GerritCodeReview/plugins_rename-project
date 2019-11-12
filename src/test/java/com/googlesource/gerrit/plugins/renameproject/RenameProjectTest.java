// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.renameproject;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@TestPlugin(
    name = "rename-project",
    sysModule = "com.googlesource.gerrit.plugins.renameproject.Module",
    sshModule = "com.googlesource.gerrit.plugins.renameproject.SshModule")
public class RenameProjectTest extends LightweightPluginDaemonTest {
  private static final String NEW_PROJECT_NAME = "newProject";

  private RenameProject renameProject;
  private Project.NameKey oldProjectKey;
  private Project.NameKey newProjectKey;
  private ProgressMonitor pm;
  private ProjectResource oldRsrc;

  @Before
  public void init() {
    renameProject = plugin.getSysInjector().getInstance(RenameProject.class);

    oldProjectKey = project;
    newProjectKey = new Project.NameKey(NEW_PROJECT_NAME);

    pm = Mockito.mock(ProgressMonitor.class);

    ProjectControl control = Mockito.mock(ProjectControl.class);
    when(control.getProject()).thenReturn(new Project(oldProjectKey));
    oldRsrc = new ProjectResource(control);
  }

  @Test
  @UseLocalDisk
  public void testRevertFromFsHandler() throws Exception {
    createChange();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);

    renameProject.fsRenameStep(oldProjectKey, newProjectKey, pm);

    ProjectState oldProjectState = projectCache.get(oldProjectKey);
    assertThat(oldProjectState).isNull();

    renameProject.performRevert(changeIds, oldProjectKey, newProjectKey, pm);
    assertReverted();
  }

  @Test
  @UseLocalDisk
  public void testRevertFromCacheHandler() throws Exception {
    createChange();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);

    renameProject.fsRenameStep(oldProjectKey, newProjectKey, pm);
    renameProject.cacheRenameStep(oldProjectKey, newProjectKey);

    ProjectState oldProjectState = projectCache.get(oldProjectKey);
    assertThat(oldProjectState).isNull();

    renameProject.performRevert(changeIds, oldProjectKey, newProjectKey, pm);
    assertReverted();
  }

  @Test
  @UseLocalDisk
  public void testRevertFromDbHandler() throws Exception {
    Result result = createChange();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);

    renameProject.fsRenameStep(oldProjectKey, newProjectKey, pm);
    renameProject.cacheRenameStep(oldProjectKey, newProjectKey);
    renameProject.dbRenameStep(changeIds, oldProjectKey, newProjectKey, pm);

    ProjectState oldProjectState = projectCache.get(oldProjectKey);
    assertThat(oldProjectState).isNull();

    // Assert that change got by NEW_PROJECT_NAME is the same change that was created
    ChangeApi changeApi = gApi.changes().id(NEW_PROJECT_NAME, result.getChange().getId().get());
    ChangeInfo changeInfo = changeApi.info();
    assertThat(changeInfo.changeId).isEqualTo(result.getChangeId());

    renameProject.performRevert(changeIds, oldProjectKey, newProjectKey, pm);
    assertReverted();
  }

  @Test
  @UseLocalDisk
  public void testRevertFromIndexHandler() throws Exception {
    createChange();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);

    renameProject.fsRenameStep(oldProjectKey, newProjectKey, pm);
    renameProject.cacheRenameStep(oldProjectKey, newProjectKey);
    renameProject.dbRenameStep(changeIds, oldProjectKey, newProjectKey, pm);
    renameProject.indexRenameStep(changeIds, oldProjectKey, newProjectKey, pm);

    ProjectState oldProjectState = projectCache.get(oldProjectKey);
    assertThat(oldProjectState).isNull();
    assertThat(queryProvider.get().byProject(oldProjectKey)).isEmpty();

    renameProject.performRevert(changeIds, oldProjectKey, newProjectKey, pm);
    assertReverted();
  }

  private void assertReverted() throws Exception {
    ProjectState oldProjectState = projectCache.get(oldProjectKey);
    assertThat(oldProjectState).isNotNull();

    ProjectState newProjectState = projectCache.get(newProjectKey);
    assertThat(newProjectState).isNull();

    assertThat(queryProvider.get().byProject(oldProjectKey)).isNotEmpty();
    assertThat(queryProvider.get().byProject(newProjectKey)).isEmpty();
  }
}

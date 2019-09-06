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

package com.googlesource.gerrit.plugins.renameproject;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.project.ProjectState;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@TestPlugin(
    name = "rename-project",
    sysModule = "com.googlesource.gerrit.plugins.renameproject.Module",
    sshModule = "com.googlesource.gerrit.plugins.renameproject.SshModule")
@UseSsh
public class RenameIT extends LightweightPluginDaemonTest {

  private static final String PLUGIN_NAME = "rename-project";
  private static final String NEW_PROJECT_NAME = "newProject";

  @Test
  @UseLocalDisk
  public void testRenameViaSshSuccessful() throws Exception {
    createChange();
    adminSshSession.exec(PLUGIN_NAME + " " + project.get() + " " + NEW_PROJECT_NAME);

    adminSshSession.assertSuccess();
    ProjectState projectState = projectCache.get(new Project.NameKey(NEW_PROJECT_NAME));
    assertThat(projectState).isNotNull();
    assertThat(queryProvider.get().byProject(project)).isEmpty();
    assertThat(queryProvider.get().byProject(new Project.NameKey(NEW_PROJECT_NAME))).isNotEmpty();
  }

  @Test
  @UseLocalDisk
  public void testRenameViaSshWithEmptyNewName() throws Exception {
    createChange();
    String newProjectName = "";
    adminSshSession.exec(PLUGIN_NAME + " " + project.get() + " " + newProjectName);

    adminSshSession.assertFailure();
    ProjectState projectState = projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNull();
  }

  @Test
  @UseLocalDisk
  public void testRenameAllProjectsFail() throws Exception {
    createChange();
    adminSshSession.exec(PLUGIN_NAME + " " + allProjects.get() + " " + NEW_PROJECT_NAME);

    adminSshSession.assertFailure();
    ProjectState projectState = projectCache.get(new Project.NameKey(NEW_PROJECT_NAME));
    assertThat(projectState).isNull();
  }

  @Test
  @UseLocalDisk
  public void testRenameExistingProjectFail() throws Exception {
    createChange();
    adminSshSession.exec(PLUGIN_NAME + " " + project.get() + " " + project.get());
    adminSshSession.assertFailure();
  }

  @Test
  @UseLocalDisk
  public void testRenameSubscribedFail() throws Exception {
    NameKey superProject = createProject("super-project");
    TestRepository<?> superRepo = cloneProject(superProject);
    NameKey subProject = createProject("subscribed-to-project");
    SubmoduleUtil.allowSubmoduleSubscription(
        metaDataUpdateFactory,
        projectCache,
        subProject,
        "refs/heads/master",
        superProject,
        "refs/heads/master",
        true);

    SubmoduleUtil.createSubmoduleSubscription(cfg, superRepo, "master", subProject.get(), "master");

    adminSshSession.exec(PLUGIN_NAME + " " + subProject.get() + " " + NEW_PROJECT_NAME);
    adminSshSession.assertFailure();
  }

  @Test
  @UseLocalDisk
  public void testRenameWatchedProject() throws Exception {
    String oldProject = project.get();
    watch(oldProject);

    List<ProjectWatchInfo> watchedProjects = gApi.accounts().self().getWatchedProjects();
    assertThat(watchedProjects.stream().allMatch(pwi -> pwi.project.equals(oldProject))).isTrue();

    adminSshSession.exec(PLUGIN_NAME + " " + oldProject + " " + NEW_PROJECT_NAME);
    adminSshSession.assertSuccess();

    watchedProjects = gApi.accounts().self().getWatchedProjects();
    assertThat(watchedProjects.stream().allMatch(pwi -> pwi.project.equals(NEW_PROJECT_NAME)))
        .isTrue();
    assertThat(watchedProjects.stream().noneMatch(pwi -> pwi.project.equals(oldProject))).isFalse();
  }
}

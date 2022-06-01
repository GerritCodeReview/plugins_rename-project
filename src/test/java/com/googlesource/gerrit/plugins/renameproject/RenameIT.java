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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change.Id;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.renameproject.RenameProject.Input;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

@TestPlugin(
    name = "rename-project",
    sysModule = "com.googlesource.gerrit.plugins.renameproject.Module",
    sshModule = "com.googlesource.gerrit.plugins.renameproject.SshModule")
@UseSsh
public class RenameIT extends LightweightPluginDaemonTest {

  private static final String PLUGIN_NAME = "rename-project";
  private static final String NEW_PROJECT_NAME = "newProject";
  private static final String NON_EXISTING_NAME = "nonExistingProject";
  private static final String CACHE_NAME = "changeid_project";
  private static final String REPLICATION_OPTION = "--replication";
  private static final String URL = "ssh://localhost:29418";
  private static final String RENAME_REGEX = "[a-zA-Z]+";

  @Inject private RequestScopeOperations requestScopeOperations;

  @Inject
  @Named(CACHE_NAME)
  private Cache<Id, String> changeIdProjectCache;

  @Test
  @UseLocalDisk
  public void testRenameViaSshSuccessful() throws Exception {
    createChange();
    adminSshSession.exec(PLUGIN_NAME + " " + project.get() + " " + NEW_PROJECT_NAME);

    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(NEW_PROJECT_NAME));
    assertThat(projectState.isPresent()).isTrue();
    assertThat(queryProvider.get().byProject(project)).isEmpty();
    assertThat(queryProvider.get().byProject(Project.nameKey(NEW_PROJECT_NAME))).isNotEmpty();
  }

  @Test
  @UseLocalDisk
  public void testRenameReplicationViaSshNotAdminUser() throws Exception {
    createChange();
    userSshSession.exec(
        PLUGIN_NAME + " " + project.get() + " " + NEW_PROJECT_NAME + " " + REPLICATION_OPTION);

    userSshSession.assertFailure();
    assertThat(userSshSession.getError()).contains("Not allowed to replicate rename");
  }

  @Test
  @UseLocalDisk
  public void testRenameReplicationViaSshAdminUser() throws Exception {
    createChange();
    adminSshSession.exec(
        PLUGIN_NAME + " " + project.get() + " " + NEW_PROJECT_NAME + " " + REPLICATION_OPTION);

    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(NEW_PROJECT_NAME));
    assertThat(projectState.isPresent()).isTrue();
  }

  @Test
  @UseLocalDisk
  public void testRenameViaSshWithEmptyNewName() throws Exception {
    createChange();
    String newProjectName = "";
    adminSshSession.exec(PLUGIN_NAME + " " + project.get() + " " + newProjectName);

    adminSshSession.assertFailure();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState.isPresent()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void testRenameAllProjectsFail() throws Exception {
    createChange();
    adminSshSession.exec(PLUGIN_NAME + " " + allProjects.get() + " " + NEW_PROJECT_NAME);

    adminSshSession.assertFailure();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(NEW_PROJECT_NAME));
    assertThat(projectState.isPresent()).isFalse();
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
  public void testRenameReplicationViaSshOnNonExisting() throws Exception {
    createChange();
    adminSshSession.exec(
        PLUGIN_NAME + " " + NON_EXISTING_NAME + " " + project.get() + " " + REPLICATION_OPTION);

    assertThat(adminSshSession.getError()).contains("project " + NON_EXISTING_NAME + " not found");
    adminSshSession.assertFailure();
  }

  @Test
  @UseLocalDisk
  public void testRenameNonExistingProjectFail() throws Exception {
    createChange();
    adminSshSession.exec(PLUGIN_NAME + " " + NON_EXISTING_NAME + " " + project.get());

    assertThat(adminSshSession.getError()).contains("project " + NON_EXISTING_NAME + " not found");
    adminSshSession.assertFailure();
  }

  @Test
  @UseLocalDisk
  public void testRenameSubscribedFail() throws Exception {
    NameKey superProject = createProjectOverAPI("super-project", null, true, null);
    TestRepository<?> superRepo = cloneProject(superProject);
    NameKey subProject = createProjectOverAPI("subscribed-to-project", null, true, null);
    SubmoduleUtil.allowSubmoduleSubscription(
        metaDataUpdateFactory,
        projectCache,
        projectConfigFactory,
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
    assertThat(watchedProjects.size()).isEqualTo(1);
  }

  @Test
  @UseLocalDisk
  public void testRenameClearedOldChangeIdLinkInCaches() throws Exception {
    Result result = createChange();
    String oldProject = project.get();

    Id changeID = result.getChange().getId();
    changeIdProjectCache.put(changeID, oldProject);

    adminSshSession.exec(PLUGIN_NAME + " " + oldProject + " " + NEW_PROJECT_NAME);
    adminSshSession.assertSuccess();

    assertThat(changeIdProjectCache.getIfPresent(changeID)).isNull();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.rename-project.url", value = URL)
  public void testReplicateRename() throws Exception {
    RenameProject renameProject = plugin.getSysInjector().getInstance(RenameProject.class);
    SshHelper sshHelper = mock(SshHelper.class);
    OutputStream errStream = mock(OutputStream.class);

    Input input = new Input();
    input.name = NEW_PROJECT_NAME;
    String expectedCommand =
        PLUGIN_NAME + " " + project.get() + " " + NEW_PROJECT_NAME + " " + REPLICATION_OPTION;

    when(sshHelper.newErrorBufferStream()).thenReturn(errStream);
    when(errStream.toString()).thenReturn("");

    renameProject.replicateRename(sshHelper, input, project);
    verify(sshHelper, atLeastOnce())
        .executeRemoteSsh(eq(new URIish(URL)), eq(expectedCommand), eq(errStream));
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.rename-project.url", value = URL)
  public void testReplicateRenameFailsOnReplicaThenRetries() throws Exception {
    RenameProject renameProject = plugin.getSysInjector().getInstance(RenameProject.class);
    RemoteSession session = mock(RemoteSession.class);
    SshHelper sshHelper = mock(SshHelper.class);
    OutputStream errStream = mock(OutputStream.class);
    when(sshHelper.newErrorBufferStream()).thenReturn(errStream);
    URIish urish = new URIish(URL);
    Input input = new Input();
    input.name = NEW_PROJECT_NAME;
    when(sshHelper.connect(eq(urish))).thenReturn(session);
    renameProject.replicateRename(sshHelper, input, project);
    String expectedCommand =
        PLUGIN_NAME + " " + project.get() + " " + NEW_PROJECT_NAME + " " + REPLICATION_OPTION;
    verify(sshHelper, times(3)).executeRemoteSsh(eq(urish), eq(expectedCommand), eq(errStream));
  }

  @Test
  @UseLocalDisk
  public void testRenameViaHttpSuccessful() throws Exception {
    createChange();
    RestResponse r = renameProjectTo(NEW_PROJECT_NAME);
    r.assertOK();

    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(NEW_PROJECT_NAME));
    assertThat(projectState.isPresent()).isTrue();
    assertThat(queryProvider.get().byProject(project)).isEmpty();
    assertThat(queryProvider.get().byProject(Project.nameKey(NEW_PROJECT_NAME))).isNotEmpty();
  }

  @Test
  @UseLocalDisk
  public void testRenameViaHttpWithEmptyNewName() throws Exception {
    createChange();
    String newProjectName = "";
    RestResponse r = renameProjectTo(newProjectName);
    r.assertBadRequest();

    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState.isPresent()).isFalse();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.rename-project.renameRegex", value = RENAME_REGEX)
  public void testRenameViaHttpWithNonMatchingNameFail() throws Exception {
    createChange();
    RestResponse r = renameProjectTo(NEW_PROJECT_NAME + "1");
    r.assertBadRequest();

    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(NEW_PROJECT_NAME));
    assertThat(projectState.isPresent()).isFalse();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.rename-project.renameRegex", value = RENAME_REGEX)
  public void testRenameViaHttpWithMatchingNameSuccess() throws Exception {
    createChange();
    RestResponse r = renameProjectTo(NEW_PROJECT_NAME);
    r.assertOK();

    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(NEW_PROJECT_NAME));
    assertThat(projectState.isPresent()).isTrue();
    assertThat(queryProvider.get().byProject(project)).isEmpty();
    assertThat(queryProvider.get().byProject(Project.nameKey(NEW_PROJECT_NAME))).isNotEmpty();
  }

  private RestResponse renameProjectTo(String newName) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    sender.clear();
    String endPoint = "/projects/" + project.get() + "/" + PLUGIN_NAME + "~rename";
    Input i = new Input();
    i.name = newName;
    i.continueWithRename = true;
    return adminRestSession.post(endPoint, i);
  }
}

package com.googlesource.gerrit.plugins.renameproject;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.googlesource.gerrit.plugins.renameproject.RenameProject.Step;
import com.googlesource.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.googlesource.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.monitor.ProgressMonitor;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@TestPlugin(
    name = "rename-project",
    sysModule = "com.googlesource.gerrit.plugins.renameproject.Module",
    sshModule = "com.googlesource.gerrit.plugins.renameproject.SshModule")
public class RenameProjectRevertTest extends LightweightPluginDaemonTest {
  private static final String NEW_PROJECT_NAME = "newProject";

  private RenameProject renameProject;
  private FilesystemRenameHandler fsHandler;
  private CacheRenameHandler cacheHandler;
  private DatabaseRenameHandler dbHandler;
  private IndexUpdateHandler indexHandler;
  private Project.NameKey oldProjectKey;
  private Project.NameKey newProjectKey;
  private ProgressMonitor pm;
  private ProjectResource oldRsrc;

  @Before
  public void init() {
    renameProject = plugin.getSysInjector().getInstance(RenameProject.class);
    fsHandler = plugin.getSysInjector().getInstance(FilesystemRenameHandler.class);
    cacheHandler = plugin.getSysInjector().getInstance(CacheRenameHandler.class);
    dbHandler = plugin.getSysInjector().getInstance(DatabaseRenameHandler.class);
    indexHandler = plugin.getSysInjector().getInstance(IndexUpdateHandler.class);
    oldProjectKey = project;
    newProjectKey = new Project.NameKey(NEW_PROJECT_NAME);

    ProjectControl control = Mockito.mock(ProjectControl.class);
    pm = Mockito.mock(ProgressMonitor.class);
    when(control.getProject()).thenReturn(new Project(project));
    oldRsrc = new ProjectResource(control);
  }

  @Test
  @UseLocalDisk
  public void testRevertFromFshHandler() throws Exception {
    createChange();
    List<Step> toRevert = new ArrayList<>();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);
    // perform some rename steps
    fsHandler.rename(oldProjectKey, newProjectKey, pm);
    toRevert.add(Step.FILESYSTEM);
    // revert performed rename steps
    renameProject.performRevert(toRevert, changeIds, oldProjectKey, newProjectKey, pm);

    assertReverted();
  }

  @Test
  @UseLocalDisk
  public void testRevertFromCacheHandler() throws Exception {
    createChange();
    List<Step> toRevert = new ArrayList<>();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);
    // perform some rename steps
    fsHandler.rename(oldProjectKey, newProjectKey, pm);
    toRevert.add(Step.FILESYSTEM);
    cacheHandler.update(oldProjectKey, newProjectKey);
    toRevert.add(Step.CACHE);
    // revert performed rename steps
    renameProject.performRevert(toRevert, changeIds, oldProjectKey, newProjectKey, pm);

    assertReverted();
  }

  @Test
  @UseLocalDisk
  public void testRevertFromDbHandler() throws Exception {
    createChange();
    List<Step> toRevert = new ArrayList<>();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);
    // perform some rename steps
    fsHandler.rename(oldProjectKey, newProjectKey, pm);
    toRevert.add(Step.FILESYSTEM);
    cacheHandler.update(oldProjectKey, newProjectKey);
    toRevert.add(Step.CACHE);
    dbHandler.rename(changeIds, oldProjectKey, newProjectKey, pm);
    toRevert.add(Step.DATABASE);
    // revert performed rename steps
    renameProject.performRevert(toRevert, changeIds, oldProjectKey, newProjectKey, pm);

    assertReverted();
  }

  @Test
  @UseLocalDisk
  public void testRevertFromIndexHandler() throws Exception {
    createChange();
    List<Step> toRevert = new ArrayList<>();
    List<Change.Id> changeIds = renameProject.getChanges(oldRsrc, pm);
    // perform some rename steps
    fsHandler.rename(oldProjectKey, newProjectKey, pm);
    toRevert.add(Step.FILESYSTEM);
    cacheHandler.update(oldProjectKey, newProjectKey);
    toRevert.add(Step.CACHE);
    dbHandler.rename(changeIds, oldProjectKey, newProjectKey, pm);
    toRevert.add(Step.DATABASE);
    indexHandler.updateIndex(changeIds, newProjectKey, pm);
    toRevert.add(Step.INDEX);
    // revert performed rename steps
    renameProject.performRevert(toRevert, changeIds, oldProjectKey, newProjectKey, pm);

    assertReverted();
  }

  private void assertReverted() throws Exception {
    ProjectState projectState = projectCache.get(project);
    assertThat(projectState).isNotNull();
    projectState = projectCache.get(newProjectKey);
    assertThat(projectState).isNull();
    assertThat(queryProvider.get().byProject(project)).isNotEmpty();
    assertThat(queryProvider.get().byProject(new Project.NameKey(NEW_PROJECT_NAME))).isEmpty();
  }
}

package com.googlesource.gerrit.plugins.renameproject;

import com.google.common.cache.Cache;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.gerrit.server.extensions.events.PluginEvent;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.conditions.RenamePreconditions;
import com.googlesource.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.googlesource.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.googlesource.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;

public class RenameAction extends RenameProject implements UiAction<ProjectResource> {
  private static final String CACHE_NAME = "changeid_project";
  @Inject
  RenameAction(DatabaseRenameHandler dbHandler,
               FilesystemRenameHandler fsHandler,
               CacheRenameHandler cacheHandler,
               RenamePreconditions renamePreconditions,
               IndexUpdateHandler indexHandler,
               Provider<CurrentUser> userProvider,
               LockUnlockProject lockUnlockProject,
               PluginEvent pluginEvent,
               @PluginName String pluginName,
               @GerritIsReplica Boolean isReplica,
               RenameLog renameLog,
               PermissionBackend permissionBackend,
               @Named(CACHE_NAME) Cache<Change.Id, String> changeIdProjectCache,
               RevertRenameProject revertRenameProject,
               SshHelper sshHelper,
               HttpSession httpSession,
               Configuration cfg) {
    super(dbHandler,
        fsHandler,
        cacheHandler,
        renamePreconditions,
        indexHandler,
        userProvider,
        lockUnlockProject,
        pluginEvent,
        pluginName,
        isReplica,
        renameLog,
        permissionBackend,
        changeIdProjectCache,
        revertRenameProject,
        sshHelper,
        httpSession,
        cfg);
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete Project")
        .setTitle("test")
        .setEnabled(true)
        .setVisible(true);
  }
}

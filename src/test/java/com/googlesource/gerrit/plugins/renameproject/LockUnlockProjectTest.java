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
import com.google.gerrit.extensions.client.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

@TestPlugin(
    name = "rename-project",
    sysModule = "com.googlesource.gerrit.plugins.renameproject.Module",
    sshModule = "com.googlesource.gerrit.plugins.renameproject.SshModule")
public class LockUnlockProjectTest extends LightweightPluginDaemonTest {

  @Inject private LockUnlockProject lockUnlockInstance;

  @Test
  public void testLockUnlockSucceeds() throws IOException, ConfigInvalidException {
    assertThat(projectCache.get(project).get().getProject().getState())
        .isEqualTo(ProjectState.ACTIVE);
    lockUnlockInstance.lock(project);
    assertThat(projectCache.get(project).get().getProject().getState())
        .isEqualTo(ProjectState.READ_ONLY);
    lockUnlockInstance.unlock(project);
    assertThat(projectCache.get(project).get().getProject().getState())
        .isEqualTo(ProjectState.ACTIVE);
  }
}

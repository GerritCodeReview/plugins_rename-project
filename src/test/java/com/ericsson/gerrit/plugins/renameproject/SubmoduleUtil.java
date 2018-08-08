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

package com.ericsson.gerrit.plugins.renameproject;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.MetaDataUpdate.Server;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

/**
 * This class provides utility methods needed for testing submodule subscription. The methods are
 * copied from the class {@link com.google.gerrit.acceptance.git.AbstractSubmoduleSubscription}.
 * Once the change related to exposing this class in the acceptance framework is merged, remove this
 * class.
 */
public class SubmoduleUtil {

  @Test
  public void emptyTest() {
    // Bazel expects to find at least one test in the classes inside this package, failing the test
    // if it does not. This empty test is then a workaround.
  }

  static void allowSubmoduleSubscription(
      Server metaDataUpdateFactory,
      ProjectCache projectCache,
      NameKey sub,
      String subBranch,
      NameKey superName,
      String superBranch,
      boolean match)
      throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(sub)) {
      md.setMessage("Added superproject subscription");
      SubscribeSection s;
      ProjectConfig pc = ProjectConfig.read(md);
      if (pc.getSubscribeSections().containsKey(superName)) {
        s = pc.getSubscribeSections().get(superName);
      } else {
        s = new SubscribeSection(superName);
      }
      String refspec;
      if (superBranch == null) {
        refspec = subBranch;
      } else {
        refspec = subBranch + ":" + superBranch;
      }
      if (match) {
        s.addMatchingRefSpec(refspec);
      } else {
        s.addMultiMatchRefSpec(refspec);
      }
      pc.addSubscribeSection(s);
      ObjectId oldId = pc.getRevision();
      ObjectId newId = pc.commit(md);
      assertThat(newId).isNotEqualTo(oldId);
      projectCache.evict(pc.getProject());
    }
  }

  static void createSubmoduleSubscription(
      Config cfg,
      TestRepository<?> repo,
      String branch,
      String subscribeToRepo,
      String subscribeToBranch)
      throws Exception {
    Config projectConfig = new Config();
    prepareSubmoduleConfigEntry(
        cfg, projectConfig, subscribeToRepo, subscribeToRepo, subscribeToBranch);
    pushSubmoduleConfig(repo, branch, projectConfig);
  }

  private static void prepareSubmoduleConfigEntry(
      Config cfg,
      Config config,
      String subscribeToRepo,
      String subscribeToRepoPath,
      String subscribeToBranch) {
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/" + subscribeToRepo;
    config.setString("submodule", subscribeToRepoPath, "path", subscribeToRepoPath);
    config.setString("submodule", subscribeToRepoPath, "url", url);
    if (subscribeToBranch != null) {
      config.setString("submodule", subscribeToRepoPath, "branch", subscribeToBranch);
    }
  }

  private static void pushSubmoduleConfig(TestRepository<?> repo, String branch, Config config)
      throws Exception {

    repo.branch("HEAD")
        .commit()
        .insertChangeId()
        .message("subject: adding new subscription")
        .add(".gitmodules", config.toText().toString())
        .create();

    repo.git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/" + branch))
        .call();
  }
}

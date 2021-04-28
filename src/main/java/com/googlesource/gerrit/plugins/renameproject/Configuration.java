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

import com.google.common.base.CharMatcher;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class Configuration {
  private static final int DEFAULT_SSH_CONNECTION_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
  private static final String URL_KEY = "url";

  private final int indexThreads;
  private final int sshCommandTimeout;
  private final int sshConnectionTimeout;

  private final Set<String> urls;
  private final String pluginName;

  @Inject
  public Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    PluginConfig cfg = pluginConfigFactory.getFromGerritConfig(pluginName);
    indexThreads = cfg.getInt("indexThreads", 4);
    sshCommandTimeout = cfg.getInt("sshCommandTimeout", 0);
    sshConnectionTimeout = cfg.getInt("sshConnectionTimeout", DEFAULT_SSH_CONNECTION_TIMEOUT_MS);

    urls =
        Arrays.stream(cfg.getStringList(URL_KEY))
            .filter(Objects::nonNull)
            .filter(s -> !s.isEmpty())
            .map(s -> CharMatcher.is('/').trimTrailingFrom(s))
            .collect(Collectors.toSet());
    this.pluginName = pluginName;
  }

  public int getIndexThreads() {
    return indexThreads;
  }

  public Set<String> getUrls() {
    return urls;
  }

  public int getSshCommandTimeout() {
    return sshCommandTimeout;
  }

  public int getSshConnectionTimeout() {
    return sshConnectionTimeout;
  }

  public String getPluginName() {
    return pluginName;
  }
}

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
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);
  private static final int DEFAULT_SSH_CONNECTION_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
  private static final int DEFAULT_TIMEOUT_MS = 5000;
  private static final String URL_KEY = "url";
  private static final String USER_KEY = "user";
  private static final String PASSWORD_KEY = "password";
  private static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
  private static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
  private static final String HTTP_SECTION = "http";
  private static final String REPLICA_SECTION = "replicaInfo";
  private static final String CHANGE_LIMIT = "changeLimit";

  private final int indexThreads;
  private final int sshCommandTimeout;
  private final int sshConnectionTimeout;
  private final int renameReplicationRetries;
  private final int connectionTimeout;
  private final int socketTimeout;
  private final String renameRegex;
  private final String user;
  private final String password;
  private final int changeLimit;
  private final Set<String> urls;

  @Inject
  public Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    PluginConfig cfg = pluginConfigFactory.getFromGerritConfig(pluginName);
    indexThreads = cfg.getInt("indexThreads", 4);
    sshCommandTimeout = cfg.getInt("sshCommandTimeout", 0);
    sshConnectionTimeout = cfg.getInt("sshConnectionTimeout", DEFAULT_SSH_CONNECTION_TIMEOUT_MS);
    renameRegex = cfg.getString("renameRegex", ".+");
    renameReplicationRetries = cfg.getInt("renameReplicationRetries", 3);
    user = Strings.nullToEmpty(cfg.getString(USER_KEY, null));
    password = Strings.nullToEmpty(cfg.getString(PASSWORD_KEY, null));
    connectionTimeout = cfg.getInt(CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    socketTimeout = cfg.getInt(SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    changeLimit = cfg.getInt(CHANGE_LIMIT, 5000);
    urls =
        Arrays.stream(cfg.getStringList(URL_KEY))
            .filter(Objects::nonNull)
            .filter(s -> !s.isEmpty())
            .map(s -> CharMatcher.is('/').trimTrailingFrom(s))
            .collect(Collectors.toSet());
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

  public String getRenameRegex() {
    return renameRegex;
  }

  public int getRenameReplicationRetries() {
    return renameReplicationRetries;
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }

  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public int getChangeLimit() {
    return changeLimit;
  }
}

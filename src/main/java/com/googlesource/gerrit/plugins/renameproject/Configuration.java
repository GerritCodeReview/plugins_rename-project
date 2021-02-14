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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {

  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  private final int indexThreads;
  private final HttpClientConfiguration httpClientConfiguration;

  @Inject
  public Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    PluginConfig cfg = pluginConfigFactory.getFromGerritConfig(pluginName);
    indexThreads = cfg.getInt("indexThreads", 4);
    Config globalPluginConfig = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    httpClientConfiguration = new HttpClientConfiguration(globalPluginConfig);
  }

  public HttpClientConfiguration httpClient() {
    return httpClientConfiguration;
  }

  public int getIndexThreads() {
    return indexThreads;
  }

  public static class HttpClientConfiguration {

    static final String USER_KEY = "user";
    static final String PASSWORD_KEY = "password";
    static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
    static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
    static final String HTTP_SECTION = "http";
    static final String URL_KEY = "url";
    static final String REPLICA_SECTION = "replicaInfo";

    static final int DEFAULT_TIMEOUT_MS = 5000;

    private final String user;
    private final String password;
    private final int connectionTimeout;
    private final int socketTimeout;
    private final Set<String> urls;

    private HttpClientConfiguration(Config cfg) {
      user = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, USER_KEY));
      password = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, PASSWORD_KEY));
      connectionTimeout = getInt(cfg, CONNECTION_TIMEOUT_KEY);
      socketTimeout = getInt(cfg, SOCKET_TIMEOUT_KEY);

      urls =
          Arrays.stream(cfg.getStringList(REPLICA_SECTION, null, URL_KEY))
              .filter(Objects::nonNull)
              .filter(s -> !s.isEmpty())
              .map(s -> CharMatcher.is('/').trimTrailingFrom(s))
              .collect(Collectors.toSet());
      log.debug("Urls: " + urls);
    }

    public Set<String> urls() {
      return ImmutableSet.copyOf(urls);
    }

    public String user() {
      return user;
    }

    public String password() {
      return password;
    }

    public int connectionTimeout() {
      return connectionTimeout;
    }

    public int socketTimeout() {
      return socketTimeout;
    }

    private int getInt(Config cfg, String name) {
      try {
        return cfg.getInt(HTTP_SECTION, name, DEFAULT_TIMEOUT_MS);
      } catch (IllegalArgumentException e) {
        log.error(
            String.format(
                "Failed to retrieve integer value for %s; using default value %d",
                name, DEFAULT_TIMEOUT_MS),
            e);
        return DEFAULT_TIMEOUT_MS;
      }
    }
  }
}

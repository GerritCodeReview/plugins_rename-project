package com.googlesource.gerrit.plugins.renameproject.rest;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.apache.http.impl.client.CloseableHttpClient;

public class RestRenameModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class).in(Scopes.SINGLETON);
    bind(HttpSession.class);
  }
}

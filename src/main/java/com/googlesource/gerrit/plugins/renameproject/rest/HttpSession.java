package com.googlesource.gerrit.plugins.renameproject.rest;

import com.google.common.base.Supplier;
import com.google.common.net.MediaType;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.renameproject.rest.HttpResponseHandler.HttpResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

public class HttpSession {

  private final CloseableHttpClient httpClient;
  private final Gson gson =
      new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();

  @Inject
  HttpSession(CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public HttpResult post(String uri) throws IOException {
    return post(uri, null);
  }

  public HttpResult post(String uri, Object content) throws IOException {
    HttpPost post = new HttpPost(uri);
    setContent(post, content);
    return httpClient.execute(post, new HttpResponseHandler());
  }

  private void setContent(HttpEntityEnclosingRequestBase request, Object content) {
    if (content != null) {
      request.addHeader("Content-Type", MediaType.JSON_UTF_8.toString());
      request.setEntity(new StringEntity(jsonEncode(content), StandardCharsets.UTF_8));
    }
  }

  private String jsonEncode(Object content) {
    if (content instanceof String) {
      return (String) content;
    }
    return gson.toJson(content);
  }
}

// Copyright (C) 2023 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.*;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class RenameProjectFilter extends AllRequestFilter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern projectNameInGerritUrl = Pattern.compile(".*/projects/([^/]+)/.*");

  private final String pluginName;
  private RenameProject renameProject;
  private Gson gson;
  private ProjectsCollection projectsCollection;

  @Inject
  public RenameProjectFilter(
      @PluginName String pluginName,
      ProjectsCollection projectsCollection,
      RenameProject renameProject) {
    this.pluginName = pluginName;
    this.projectsCollection = projectsCollection;
    this.renameProject = renameProject;
    this.gson = OutputFormat.JSON.newGsonBuilder().create();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletResponse httpResponse = (HttpServletResponse) response;
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    if (isRenameAction(httpRequest)) {
      try {
        writeResponse(httpResponse, renameProject(httpRequest));
      } catch (RestApiException
          | PermissionBackendException
          | ConfigInvalidException
          | RenameRevertException
          | InterruptedException e) {
        throw new ServletException(e);
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  private <T> void writeResponse(HttpServletResponse httpResponse, Response<T> response)
      throws IOException {
    String responseJson = gson.toJson(response);
    if (response.statusCode() == SC_OK) {

      httpResponse.setContentType("application/json");
      httpResponse.setStatus(response.statusCode());
      PrintWriter writer = httpResponse.getWriter();
      writer.print(new String(RestApiServlet.JSON_MAGIC));
      writer.print(responseJson);
    } else {
      httpResponse.sendError(response.statusCode(), responseJson);
    }
  }

  private boolean isRenameAction(HttpServletRequest httpRequest) {
    return httpRequest.getRequestURI().endsWith(String.format("/%s~rename", pluginName));
  }

  private Response<String> renameProject(HttpServletRequest httpRequest)
      throws RestApiException,
          IOException,
          PermissionBackendException,
          ConfigInvalidException,
          RenameRevertException,
          InterruptedException {
    RenameProject.Input input = readJson(httpRequest, TypeLiteral.get(RenameProject.Input.class));
    IdString id = getProjectName(httpRequest).get();

    ProjectResource projectResource = projectsCollection.parse(TopLevelResource.INSTANCE, id);

    return (Response<String>) renameProject.apply(projectResource, input);
  }

  private Optional<IdString> getProjectName(HttpServletRequest req) {
    return extractProjectName(req, projectNameInGerritUrl);
  }

  private Optional<IdString> extractProjectName(HttpServletRequest req, Pattern urlPattern) {
    String path = req.getRequestURI();
    Matcher projectGroupMatcher = urlPattern.matcher(path);

    if (projectGroupMatcher.find()) {
      return Optional.of(IdString.fromUrl(projectGroupMatcher.group(1)));
    }

    return Optional.empty();
  }

  private <T> T readJson(HttpServletRequest httpRequest, TypeLiteral<T> typeLiteral)
      throws IOException, BadRequestException {

    try (BufferedReader br = httpRequest.getReader();
        JsonReader json = new JsonReader(br)) {
      try {
        json.setLenient(true);

        try {
          json.peek();
        } catch (EOFException e) {
          throw new BadRequestException("Expected JSON object", e);
        }

        return gson.fromJson(json, typeLiteral.getType());
      } finally {
        try {
          // Reader.close won't consume the rest of the input. Explicitly consume the request
          // body.
          br.skip(Long.MAX_VALUE);
        } catch (Exception e) {
          // ignore, e.g. trying to consume the rest of the input may fail if the request was
          // cancelled
          logger.atFine().withCause(e).log("Exception during the parsing of the request json");
        }
      }
    }
  }
}

// Copyright (C) 2020 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(RenameProjectCapability.RENAME_PROJECT)
@Export("/rename")
@Singleton
public class RenameProjectServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final String CONTINUE_RENAME_ANSWER = "yes";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RenameProjectHandler renameProjectHandler;

  @Inject
  RenameProjectServlet(RenameProjectHandler renameProjectHandler) {
    this.renameProjectHandler = renameProjectHandler;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    StringWriter stringWriter = new StringWriter();
    try {
      BufferedReader reader = req.getReader();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      InputStream in = new ByteArrayInputStream(CONTINUE_RENAME_ANSWER.getBytes());
      Map<String, String> input =
          new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
      renameProjectHandler.renameProject(
          input.get("newProjectName"), input.get("oldProjectName"), printWriter, in);
      res.setStatus(SC_NO_CONTENT);
    } catch (AuthException
        | BadRequestException
        | ResourceConflictException
        | NoSuchProjectException e) {
      res.sendError(SC_BAD_REQUEST);
      logger.atSevere().withCause(e).log(e.getMessage());
    } catch (ConfigInvalidException
        | RenameRevertException
        | InterruptedException
        | IOException e) {
      res.sendError(SC_INTERNAL_SERVER_ERROR);
      logger.atSevere().withCause(e).log(
          String.format("Failed to rename project. Performed steps: %s", stringWriter.toString()));
    }
  }
}

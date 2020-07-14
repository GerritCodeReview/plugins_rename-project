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

import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Export("/rename")
@Singleton
public class RenameProjectServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;

  private final RenameProjectHandler renameProjectHandler;
  private final Provider<CurrentUser> self;

  @Inject
  RenameProjectServlet(RenameProjectHandler renameProjectHandler, Provider<CurrentUser> self) {
    this.renameProjectHandler = renameProjectHandler;
    this.self = self;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      BufferedReader reader = req.getReader();
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      Map<String, String> input =
          new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
      res.setContentType("text/plain");
      res.setCharacterEncoding("UTF-8");
      renameProjectHandler.renameProject(
          input.get("newProjectName"), input.get("oldProjectName"), printWriter, self.get());
      res.getWriter().write(stringWriter.toString());
    } catch (AuthException
        | BadRequestException
        | ResourceConflictException
        | InterruptedException
        | ConfigInvalidException
        | RenameRevertException
        | NoSuchProjectException e) {
      res.sendError(SC_BAD_REQUEST);
      e.printStackTrace();
    }
  }
}

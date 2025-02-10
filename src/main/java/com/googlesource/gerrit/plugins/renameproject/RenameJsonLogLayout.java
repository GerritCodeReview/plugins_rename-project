// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.log4j.spi.LoggingEvent;

final class RenameJsonLogLayout extends JsonLayout {
  static class RenameJsonLogEntry extends JsonLogEntry {
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("'['yyyy-MM-dd HH:mm:ss,SSS xxxx']'");
    public String timestamp;
    public String level;
    public String accountId;
    public String userName;
    public String projectName;
    public JsonObject options;
    public String error;
    public String message;

    @SerializedName("@version")
    public final int version = 1;

    public RenameJsonLogEntry(LoggingEvent event) {
      timestamp = formatDate(event.getTimeStamp());
      level = event.getLevel().toString();
      accountId = (String) event.getMDC(RenameLog.ACCOUNT_ID);
      userName = (String) event.getMDC(RenameLog.USER_NAME);
      projectName = (String) event.getMDC(RenameLog.PROJECT_NAME);
      options = JsonParser.parseString((String) event.getMDC(RenameLog.OPTIONS)).getAsJsonObject();
      error = (String) event.getMDC(RenameLog.ERROR);
      message = event.getMessage().toString();
    }

    private String formatDate(long now) {
      ZonedDateTime zdt =
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
      return zdt.format(DATE_FORMATTER);
    }
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LoggingEvent event) {
    return new RenameJsonLogEntry(event);
  }
}

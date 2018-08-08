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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.util.QuotedString;

final class RenameLogLayout extends Layout {
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("'['yyyy-MM-dd HH:mm:ss,SSS xxxx']'");

  /**
   * Formats the events in the rename log.
   *
   * <p>A successful project rename will result in a log entry like this: [2015-03-05 09:13:28,912
   * +0100] INFO 1000000 admin OK \ myProject {"name": newName}
   *
   * <p>The log entry for a failed project rename will look like this: [2015-03-05 12:14:30,180
   * +0100] ERROR 1000000 admin FAIL \ myProject {"name": newName}
   * com.google.gwtorm.server.OrmException: \ Failed to access the database
   */
  @Override
  public String format(LoggingEvent event) {
    StringBuilder buf = new StringBuilder(128);

    buf.append(formatDate(event.getTimeStamp()));

    buf.append(' ');
    buf.append(event.getLevel().toString());

    req(RenameLog.ACCOUNT_ID, buf, event);
    req(RenameLog.USER_NAME, buf, event);

    buf.append(' ');
    buf.append(event.getMessage());

    req(RenameLog.PROJECT_NAME, buf, event);
    opt(RenameLog.OPTIONS, buf, event);
    opt(RenameLog.ERROR, buf, event);

    buf.append('\n');
    return buf.toString();
  }

  private String formatDate(long now) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
    return zdt.format(DATE_FORMATTER);
  }

  private void req(String key, StringBuilder builder, LoggingEvent event) {
    Object val = event.getMDC(key);
    builder.append(' ');
    if (val != null) {
      String s = val.toString();
      if (s.contains(" ")) {
        builder.append(QuotedString.BOURNE.quote(s));
      } else {
        builder.append(val);
      }
    } else {
      builder.append('-');
    }
  }

  private void opt(String key, StringBuilder builder, LoggingEvent event) {
    Object val = event.getMDC(key);
    if (val != null) {
      builder.append(' ');
      builder.append(val);
    }
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {
    // not needed
  }
}

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

package com.ericsson.gerrit.plugins.renameproject.monitor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandProgressMonitor implements ProgressMonitor, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(CommandProgressMonitor.class);
  private final Writer out;
  private boolean write = true;
  private Task task;

  public CommandProgressMonitor(Writer out) {
    this.out = out;
  }

  @Override
  public synchronized void beginTask(String taskName, int work) {
    endTask();
    if (work == ProgressMonitor.UNKNOWN) {
      task = new UnknownTask(taskName);
    } else {
      task = new Task(taskName, work);
    }
  }

  @Override
  public synchronized void update(int completed) {
    if (task != null) {
      task.update(completed);
    }
  }

  private void endTask() {
    if (task != null) {
      task.end();
      task = null;
    }
  }

  @Override
  public synchronized void close() {
    endTask();
  }

  private class Task {
    protected final String taskName;
    protected final int totalWork;
    protected int completedWork;
    protected boolean taskDone;

    Task(String taskName, int totalWork) {
      this.taskName = taskName;
      this.totalWork = totalWork;
      sendUpdate();
    }

    void update(int completed) {
      completedWork += completed;
      sendUpdate();
    }

    void end() {
      taskDone = true;
      sendUpdate();
    }

    protected void sendUpdate() {
      if (!write) {
        return;
      }
      StringBuilder output = new StringBuilder();
      writeTaskName(output);
      writeDetails(output);
      if (taskDone) {
        output.append("\n");
      }
      try {
        out.write(output.toString());
        out.flush();
      } catch (IOException err) {
        write = false;
        log.error("Failed to send update to the progress monitor:", err);
      }
    }

    protected void writeTaskName(StringBuilder output) {
      output.append("\r");
      output.append(taskName);
      output.append(": ");
      while (output.length() < 50) {
        output.append(' ');
      }
    }

    protected void writeDetails(StringBuilder output) {
      String totalStr = String.valueOf(totalWork);
      StringBuilder completedStr = new StringBuilder(String.valueOf(completedWork));
      while (completedStr.length() < totalStr.length()) {
        completedStr.insert(0, " ");
      }
      int percentDone = completedWork * 100 / totalWork;
      if (percentDone < 100) {
        output.append(' ');
      }
      output.append(percentDone);
      output.append("% (");
      output.append(completedStr);
      output.append("/");
      output.append(totalStr);
      output.append(")");
    }
  }

  private class UnknownTask extends Task {
    private static final String ANIMATION = "|/-\\";
    private static final int ANIMATION_DELAY_MS = 100;
    private final ScheduledThreadPoolExecutor executor;
    private int animationCounter;

    UnknownTask(String taskName) {
      super(taskName, ProgressMonitor.UNKNOWN);
      executor =
          new ScheduledThreadPoolExecutor(
              1, new ThreadFactoryBuilder().setNameFormat("Rename-Monitoring-%d").build());
      executor.scheduleAtFixedRate(
          () -> {
            animationCounter++;
            sendUpdate();
          },
          ANIMATION_DELAY_MS,
          ANIMATION_DELAY_MS,
          TimeUnit.MILLISECONDS);
    }

    @Override
    protected void writeDetails(StringBuilder output) {
      if (taskDone) {
        output.append("100%");
        return;
      }
      int nextAnimation = animationCounter % ANIMATION.length();
      output.append(String.valueOf(ANIMATION.charAt(nextAnimation)));
    }

    @Override
    void end() {
      executor.shutdownNow();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.error("Interrupted while waiting for termination of executor:", e);
        Thread.currentThread().interrupt();
      }
      super.end();
    }
  }
}

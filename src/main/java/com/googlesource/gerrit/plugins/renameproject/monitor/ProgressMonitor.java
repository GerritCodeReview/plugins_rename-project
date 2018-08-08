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

package com.googlesource.gerrit.plugins.renameproject.monitor;

public interface ProgressMonitor {

  /** Constant indicating the total work units cannot be predicted. */
  int UNKNOWN = 0;

  /**
   * Begin processing a single task.
   *
   * @param title title to describe the task.
   * @param totalWork total number of work units the application will perform; {@link #UNKNOWN} if
   *     it cannot be predicted in advance.
   */
  void beginTask(String title, int totalWork);

  /**
   * Denote that some work units have been completed.
   *
   * <p>This is an incremental update; if invoked once per work unit the correct value for our
   * argument is <code>1</code>, to indicate a single unit of work has been finished by the caller.
   *
   * @param completed the number of work units completed since the last call.
   */
  void update(int completed);

  /** End the monitoring. */
  void close();
}

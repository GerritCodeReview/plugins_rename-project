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

/** A NoopMonitor does not report progress anywhere. */
public class NoopMonitor implements ProgressMonitor {

  public static final NoopMonitor INSTANCE = new NoopMonitor();

  private NoopMonitor() {
    // Do not let others instantiate
  }

  @Override
  public void beginTask(String title, int totalWork) {
    // Do not report.
  }

  @Override
  public void beginTask(String title) {
    // Do not report.
  }

  @Override
  public void update(int completed) {
    // Do not report.
  }

  @Override
  public void close() {
    // Do not report.
  }
}

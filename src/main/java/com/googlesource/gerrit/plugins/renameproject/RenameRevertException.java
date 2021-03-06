// Copyright (C) 2019 The Android Open Source Project
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

/** Add cause for exception during revert operation */
public class RenameRevertException extends Exception {
  private static final long serialVersionUID = 1L;

  public RenameRevertException(Throwable revertException, Throwable cause) {
    super(
        "Failed to revert after failed rename. Revert cause: " + cause.getMessage(),
        revertException.initCause(cause));
  }
}

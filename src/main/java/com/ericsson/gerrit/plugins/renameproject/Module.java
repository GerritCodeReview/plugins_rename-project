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

import static com.ericsson.gerrit.plugins.renameproject.RenameOwnProjectCapability.RENAME_OWN_PROJECT;
import static com.ericsson.gerrit.plugins.renameproject.RenameProjectCapability.RENAME_PROJECT;

import com.ericsson.gerrit.plugins.renameproject.cache.CacheRenameHandler;
import com.ericsson.gerrit.plugins.renameproject.conditions.RenamePreconditions;
import com.ericsson.gerrit.plugins.renameproject.database.DatabaseRenameHandler;
import com.ericsson.gerrit.plugins.renameproject.database.IndexUpdateHandler;
import com.ericsson.gerrit.plugins.renameproject.fs.FilesystemRenameHandler;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.AbstractModule;
import com.google.inject.internal.UniqueAnnotations;

public class Module extends AbstractModule {

  @Override
  protected void configure() {
    bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create()).to(RenameLog.class);
    bind(CacheRenameHandler.class);
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(RENAME_PROJECT))
        .to(RenameProjectCapability.class);
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(RENAME_OWN_PROJECT))
        .to(RenameOwnProjectCapability.class);
    bind(DatabaseRenameHandler.class);
    bind(FilesystemRenameHandler.class);
    bind(RenamePreconditions.class);
    bind(IndexUpdateHandler.class);
  }
}

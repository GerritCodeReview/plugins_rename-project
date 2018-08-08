Configuration
=============

The configuration of the @PLUGIN@ plugin is done in the `gerrit.config`
file.

Expected Configuration
----------------------
Provides an option to configure the number of threads used for indexing
the changes. The default value used for this option is 4.

```
  [plugin "@PLUGIN@"]
    indexThreads = 4
```

# How to e2e-test this plugin

Consider the
[instructions](https://gerrit-review.googlesource.com/Documentation/dev-e2e-tests.html)
on how to use Gerrit core's Gatling framework, to run non-core test
scenarios such as this plugin one below:

```
  $ sbt "gatling:testOnly com.googlesource.gerrit.plugins.renameproject.scenarios.RenameProject"
```

Scenario scala source files and their companion json resource ones are
stored under the usual src/test directories. That structure follows the
scala package one from the scenario classes. The core framework expects
such a directory structure for both the scala and resources (json data)
files.

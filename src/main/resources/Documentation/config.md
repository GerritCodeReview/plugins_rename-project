Configuration
=============

The configuration of the @PLUGIN@ plugin is done in the `gerrit.config`
file and rename replication is configured in `$site_path/etc/@PLUGIN@.config`.

Expected Configuration
----------------------
Provides an option to configure the number of threads used for indexing
the changes. The default value used for this option is 4.

```
  [plugin "@PLUGIN@"]
    indexThreads = 4
```

Rename project replication is enabled by adding the `replicaInfo` section with appropriate `url`'s.
For example:
```
  [replicaInfo]
    url = http://mirror1.us.some.org:8080
    url = http://mirror2.us.some.org:8080
    url = http://mirror3.us.some.org:8080
```

@PLUGIN@ plugin uses REST API calls to replicate the rename operation. It is possible to customize
the parameters of the underlying http client doing these calls by specifying the following fields:

* `http.user` : Username to connect to the replica instance.

* `http.password` : Password to connect to the replica instance.

* `http.connectionTimeout` : The maximum interval of time in milliseconds the plugin waits for a
connection to the replica instance. When not specified, the default value is set to 5000ms.

* `http.socketTimeout` : The maximum interval of time in milliseconds the plugin waits for a response
from the replica instance once the connection has been established. When not specified, the default
value is set to 5000ms.

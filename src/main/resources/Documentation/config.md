Configuration
=============

The configuration of the @PLUGIN@ plugin is done in the `gerrit.config`
file and rename propagation is configured in `$site_path/etc/@PLUGIN@.config`

Expected Configuration
----------------------
Provides an option to configure the number of threads used for indexing
the changes. The default value used for this option is 4.

```
  [plugin "@PLUGIN@"]
    indexThreads = 4
```

Rename project propagation is enabled by adding `replicaInfo` section with appropriate
`url`'s. For example:
```
[replicaInfo]
    url = http://mirror1.us.some.org:8080
    url = http://mirror2.us.some.org:8080
    url = http://mirror3.us.some.org:8080
```

@PLUGIN@ plugin uses REST API calls to propagating the rename operation. It is possible to customize
the parameters of the underlying http client doing these calls by specifying the following fields:

http.user : Username to connect to the replica instance.

http.password : Password to connect to the replica instance.

http.connectionTimeout : Maximum interval of time in milliseconds the plugin waits for a connection
to the target instance. When not specified, the default value is set to 5000ms.

http.socketTimeout : Maximum interval of time in milliseconds the plugin waits for a response from
the target instance once the connection has been established. When not specified, the default value
is set to 5000ms.

http.maxTries : Maximum number of times the plugin should attempt when calling a REST API in the
target instance. Setting this value to 0 will disable retries. When not specified, the default value
is 360. After this number of failed tries, an error is logged.

http.retryInterval : The interval of time in milliseconds between the subsequent auto-retries. When
not specified, the default value is set to 10000ms.

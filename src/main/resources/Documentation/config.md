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

Rename project replication is enabled by adding appropriate `url`'s.
For example:

```
  [plugin "@PLUGIN@"]
    url = ssh://admin@mirror1.us.some.org
    url = ssh://mirror2.us.some.org:29418
    url = http://localhost:8080
```
The plugin supports both http and ssh replication.

To configure ssh replication, specify the port number and put the `ssh://`
prefix followed by hostname and then port number after `:`. It is also possible
to specify the ssh user by passing `USERNAME@` as a prefix for hostname.

If rename replication is done over SSH, ensure the host key of the remote
system(s) is already in the Gerrit user's `~/.ssh/known_hosts` file. The easiest
way to add the host key is to connect once by hand with the command line:

```
  sudo su -c 'ssh mirror1.us.some.org echo' gerrit2
```
It is possible to customize the parameters of the underlying ssh client doing
these calls by specifying the following fields:
* `sshCommandTimeout` : Timeout for SSH command execution. If 0, there is no
timeout, and the client waits indefinitely. By default, 0.
* `sshConnectionTimeout` : Timeout for SSH connections in minutes. If 0, there
is no timeout, and the client waits indefinitely. By default, 2 minutes.

To configure http replication, provide the correct url. To specify username and
password for replication for rename, add password and username in gerrit.config
or secure.config. for example:
```
  [plugin "@PLUGIN@"]
    user = username
    password = userpassword
```

Provides a configuration to customize the number of rename replication retries.
By default, 3.

```
  renameReplicationRetries = 6
```

Also, this plugin offers a way to restrict the new names of the projects to
match an optionally configured regex. For example:

```
  [plugin "@PLUGIN@"]
    renameRegex = [a-z0-9]+
```

In this example the new names for projects will be restricted to only
non-capital letters and numbers.

Enables administrators to restrict the renaming of projects that have more than
the configured number of changes.

```
  [plugin "@PLUGIN@"]
    changeLimit = 0
```
The above example config restricts renaming projects with more than zero changes.

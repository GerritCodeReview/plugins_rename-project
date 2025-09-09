The @PLUGIN@ plugin provides the ability to rename a Gerrit project, including
its Git repository and all associated changes. It supports replication of
renames across Gerrit replicas via SSH or HTTP.

Note: The project state is set to READ_ONLY at the start of the rename operation.
If the rename operation fails, the project state is reverted back to ACTIVE.

Limitations
-----------

There are a few caveats:

* You cannot rename projects that use "submodule subscription"

* You cannot rename projects that have any child projects

* You cannot rename using a project name that already exists

* You cannot rename the "All-Projects" project

* You cannot rename the "All-Users" project

Replication of project renaming
-------------------------------

This plugin can replicate project renaming by itself, if `gerrit.config` has a `url` entry at the
plugin configuration section and if master and all other replicas have this plugin installed. Once
configured, replication of rename will start on every successful renaming of a local project. When
the plugin completes the renaming operation on the master instance successfully, it sends an SSH
command or HTTP request to replicas' rename-project plugin using the hostname provided in the
configuration file. Replicas then perform their own local file system rename.

For more details on how to configure replication, see the [Configuration](config.md) documentation.

The rename replication will retry as many times as provided by the configuration parameter
`renameReplicationRetries`. The default value is 3 and can be configured in `gerrit.config`.
If rename replication fails after the mentioned number of retries, the plugin stops retrying
the rename replication operation and logs the error. This results in primary and replica instances
being out of sync. The admin then will have to manually perform the rename operation on the replica
instance.

Access
------

To be allowed to rename arbitrary projects, a user must be a member of a
group that is granted the 'Rename Project' capability (provided by this
plugin) or the 'Administrate Server' capability. Project owners are
allowed to rename their own projects if they are members of a group that
is granted the 'Rename Own Project' capability (provided by this
plugin).

However, because of all the [caveats](#limitations)  of this plugin, it is not
recommended to delegate the 'Rename Project' capability to any non-admin user.

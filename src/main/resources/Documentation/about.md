Provides the ability to rename a project.

Note: The project state is set to READ_ONLY at the start of the rename operation.
If the rename operation fails, the project state is reverted back to ACTIVE.

Limitations
-----------

There are a few caveats:

* You cannot rename projects that use "submodule subscription"

     Projects that use submodule subscription cannot be renamed.
     Remove the submodule registration before attempting to rename the project.

* You cannot rename projects that have any child projects

     Projects that have child projects cannot be renamed. Currently, the
     plugin does not support rename of parent projects.

* You cannot rename using a project name that already exists

     If choosing a name that already exists, you cannot rename the project.

* You cannot rename the "All-Projects" project

     If choosing to rename "All-Projects", you cannot rename the project as this action is prohibited.

* You cannot rename the "All-Users" project

     If choosing to rename "All-Users", you cannot rename the project as this action is prohibited.

* You should limit project renames to administrator users

     Because of all the above caveats, it is not recommended to allow any non-admin
     user to perform any project rename.

Replication of project renaming
-------------------------------

This plugin can replicate project renaming by itself, if `gerrit.config` has a `url` entry at the
plugin configuration section and if master and all other replicas have this plugin installed. Once
configured, replication of rename will start on every successful renaming of a local project. When
the plugin completes the renaming operation on the master instance successfully, it sends an ssh
command to replicas' rename-project plugin using the hostname provided in the configuration file.
Replicas then perform their own local file system rename.

The ssh rename replication will retry as many times as provided by the configuration parameter
`renameReplicationRetries`. The default value is 3 and can be configured in `gerrit.config`.
If ssh rename replication fails after the mentioned number of retries, the plugin stops retrying
the rename replication operation and logs the error. This results in primary and replica instances
being out of sync. The admin then will have to consider the following steps:

1. Start replication on the renamed project from the primary side. For more information, see the
[replication start command](../../replication/Documentation/cmd-start.md).

2. Confirm that the renamed project was replicated.

3. Delete the original (not renamed) project repository directory contents on the replica side; this
step is optional.

Access
------

To be allowed to rename arbitrary projects, a user must be a member of a
group that is granted the 'Rename Project' capability (provided by this
plugin) or the 'Administrate Server' capability. Project owners are
allowed to rename their own projects if they are members of a group that
is granted the 'Rename Own Project' capability (provided by this
plugin). However, because of all the caveats of this plugin, it is not
recommended to delegate the 'Rename Project' capability to any non-admin user.

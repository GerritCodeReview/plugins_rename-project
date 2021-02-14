Provides the ability to rename a project.

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

This plugin can replicate project renaming by itself if configuration file `@PLUGIN@.config` has
`replicaInfo.url` entry and if master and all other replicas has this plugin installed. 
Once configured, replication of rename will start on every successful renaming of a local project.
When the plugin completes renaming operation on the master instance successfully, the plugin sends
a http request to replicas' rename-project endpoints provided in the configuration file.

Access
------

To be allowed to rename arbitrary projects, a user must be a member of a
group that is granted the 'Rename Project' capability (provided by this
plugin) or the 'Administrate Server' capability. Project owners are
allowed to rename their own projects if they are members of a group that
is granted the 'Rename Own Project' capability (provided by this
plugin). However, because of all the caveats of this plugin, it is not
recommended to delegate the 'Rename Project' capability to any non-admin user.

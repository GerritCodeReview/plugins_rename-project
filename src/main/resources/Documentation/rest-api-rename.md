@PLUGIN@ - /@PLUGIN@/ REST API
===================================

This page describes the REST endpoint that is added by the @PLUGIN@
plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

This API implements a REST equivalent of the Ssh rename-project command.
For more information, refer to:
* [Ssh rename-project command](cmd-rename.md)
------------------------------------------

REQUEST
-------
```
POST /projects/project-1/@PLUGIN@~rename HTTP/1.1
  {
    "name" : "project-2",
  }
```
to rename project-1 to project-2.

By default, if project-1 has more than 5000 changes, the rename procedure will be cancelled as it
can take longer time and can degrade in performance in that time frame.

To rename a project with more than 5000 changes, the following request is needed:
```
POST /projects/project-1/@PLUGIN@~rename HTTP/1.1
  {
    "name" : "project-2",
    "continueWithRename" : "true"
  }
```

To perform only file system rename the following request is needed:
```
POST /projects/project-1/@PLUGIN@~rename HTTP/1.1
  {
    "name" : "project-2",
    "replication" : "true"
  }
```
By default, the value of `replication` is false. This http request is used for replication of
rename operation to other replica instances. This command should not be used on non-replica
instances.

RESPONSE
--------
If rename succeeded:

```
HTTP/1.1 200 OK
```

If rename was cancelled due to user's intent to not proceed when the number of changes exceeds the
warning limit of 5000 changes:

```
HTTP/1.1 204 No Content
```

ACCESS
------
Same as ssh version of the command, caller must be a member of a group that is granted the
'Rename Project' (provided by this plugin) or 'Administrate Server' capabilities.

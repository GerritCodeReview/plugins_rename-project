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
POST /plugins/rename-project/rename HTTP/1.0
  {
    "oldProjectName" : "project-1",
    "newProjectName" : "project-2",
  }
```
to rename project-1 to project-2.

RESPONSE
--------
```
HTTP/1.1 200
Checking preconditions:                          100%
Retrieving the list of changes from DB:          100%
Renaming git repository:                         100%
Updating changes in the database:                100%
Indexing changes:                                100%
```

ACCESS
------
Same as ssh version of the command, caller must be a member of a group that is granted the
'Rename Project' (provided by this plugin) or 'Administrate Server' capabilities.
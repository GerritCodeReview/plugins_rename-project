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

By default, if project-1 has more than 5000 changes rename procedure will be cancelled.

To rename project with more than 5000 changes, following requests is needed:
```
POST /plugins/rename-project/rename HTTP/1.0
  {
    "oldProjectName" : "project-1",
    "newProjectName" : "project-2",
    "continue' : "yes"
  }
```

RESPONSE
--------
```
HTTP/1.1 204
```

ACCESS
------
Same as ssh version of the command, caller must be a member of a group that is granted the
'Rename Project' (provided by this plugin) or 'Administrate Server' capabilities.
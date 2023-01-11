@PLUGIN@ rename
===============

NAME
----
@PLUGIN@ rename - Rename a project and update all its changes

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@
  <PROJECT>
  <NEWNAME>
```

DESCRIPTION
-----------
Renames a project in the Gerrit installation, renaming the Git
repository along with updating any changes associated with it.

ACCESS
------
Caller must be a member of a group that is granted the 'Rename Project'
capability (provided by this plugin) or the 'Administrate Server'
capability.

SCRIPTING
---------
This command is intended to be used in scripts.

EXAMPLES
--------

Rename a project named 'project-1' to 'project-2':

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ project-1 project-2
```


SEE ALSO
--------

* [Access Control](../../../Documentation/access-control.html)

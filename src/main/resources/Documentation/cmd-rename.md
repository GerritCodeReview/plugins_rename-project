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
  [--replication]
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


OPTIONS
-------
`--replication`
:   To perform only file system rename. This option is used for replication of
    rename operation to other replica instances. This command should not be used
    towards non-replica instances. This option requires the user to have admin
    permissions.

EXAMPLES
--------

Rename a project named 'project-1' to 'project-2':

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ project-1 project-2
```


SEE ALSO
--------

* [Access Control](../../../Documentation/access-control.html)

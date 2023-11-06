workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "f9c119e45d9a241bee720b7fbd6c7fdbc952da5f",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api(version = "3.7.0",
           plugin_api_sha1 = "95ceafdaea39b21b0998d5b9109a2aaf4d4be83b",
           acceptance_framework_sha1 = "1546658913a21996b7635f832aac44a7ca3554c6")
load("//:deps.bzl", "deps")

deps()

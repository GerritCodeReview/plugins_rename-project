workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "74f9d8e76d5014d218ae6fe55127a5288c9a32c3",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api(version = "3.3.0-SNAPSHOT")

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()

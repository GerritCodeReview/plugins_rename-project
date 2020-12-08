workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "a511f3c90129d7de7ae67c0637001162980c08d5",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api(version = "3.3.0")

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()

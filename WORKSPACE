workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "2c39029a585bd1d5b785150948f162730f7b7e42",
)

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()

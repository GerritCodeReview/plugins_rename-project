workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "a2830809bc277f55e094e8203fee04d9dc802e51",
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

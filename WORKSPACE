workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "8386b3fbf80e375f0a10c8386c0a8dfe260c5c1b",
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

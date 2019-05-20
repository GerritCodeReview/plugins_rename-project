workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "62c3c8e0b4b767d1f3b78998ad1aba42a44a9661",
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

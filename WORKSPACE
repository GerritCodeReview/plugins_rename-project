workspace(name = "rename_project")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "88570121c377a8a94c76915566114bf02113a7d9",
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
